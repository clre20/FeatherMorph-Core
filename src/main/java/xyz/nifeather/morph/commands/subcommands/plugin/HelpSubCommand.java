package xyz.nifeather.morph.commands.subcommands.plugin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Messages.FormattableMessage;
import xyz.nifeather.morph.FeatherMorphMain;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.commands.MorphCommandManager;
import xyz.nifeather.morph.commands.brigadier.IConvertibleBrigadier;
import xyz.nifeather.morph.commands.subcommands.plugin.helpsections.Entry;
import xyz.nifeather.morph.commands.subcommands.plugin.helpsections.Section;
import xyz.nifeather.morph.messages.strings.HelpStrings;
import xyz.nifeather.morph.messages.MessageUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class HelpSubCommand extends MorphPluginObject implements IConvertibleBrigadier
{
    @Override
    public String name()
    {
        return "help";
    }

    @Initializer
    private void load()
    {
        setupCommandSections();
    }

    @Resolved
    private MorphCommandManager cmdHelper;

    private final List<Section> commandSections = new CopyOnWriteArrayList<>();

    /**
     * 设置用于构建帮助信息的Section
     */
    private void setupCommandSections()
    {
        //不属于任何section的指令丢到这里
        var miscCommandSection = new Section("/", HelpStrings.morphCommandDescription(), ObjectList.of(
                HelpStrings.morphCommandSpecialNote1(),
                HelpStrings.morphCommandSpecialNote2()
        ));

        commandSections.add(miscCommandSection);

        //遍历所有指令
        for (var c : cmdHelper.commands())
        {
            //如果指令拥有子指令，新建section
            if (!c.children().isEmpty())
            {
                //此section下所有指令的父级指令
                var parentCommandName = c.name();

                List<FormattableMessage> notes = new ObjectArrayList<>(c.getNotes());

                var section = new Section(parentCommandName,
                        c.getHelpMessage(),
                        notes);

                //添加指令到section中
                for (var sc : c.children())
                {
                    var cmdName = parentCommandName + " " + sc.name();
                    section.add(new Entry(sc.permission(),
                            cmdName,
                             sc.getHelpMessage(),
                            "/" + cmdName));
                }

                commandSections.add(section);
            }
            else
            {
                miscCommandSection.add(new Entry(c.permission(),
                        c.name(),
                        c.getHelpMessage(),
                        "/" + c.name()));
            }
        }
    }

    /**
     * 區段在說明頁顯示的分類名稱
     */
    private FormattableMessage categoryName(Section section)
    {
        return switch (section.getCommandBaseName())
        {
            case "/" -> HelpStrings.categoryDisguise();
            case "request" -> HelpStrings.categoryRequest();
            case "feathermorph" -> HelpStrings.categoryPlugin();
            default -> new FormattableMessage(FeatherMorphMain.getInstance(), "/" + section.getCommandBaseName());
        };
    }

    /**
     * 顯示用的指令：/feathermorph 縮寫為 /fm
     */
    private static String displayCommand(String baseName)
    {
        if (baseName.equals("feathermorph") || baseName.startsWith("feathermorph "))
            baseName = "fm" + baseName.substring("feathermorph".length());

        return "/" + baseName;
    }

    /**
     * 建構一個分類（標題 + sender 有權限的指令）；沒有任何可用指令時回傳空清單
     */
    private List<Component> constructSection(CommandSender sender, Section section, String locale)
    {
        var lines = new ObjectArrayList<Component>();

        for (var entry : section.getEntries())
        {
            var perm = entry.permission();
            if (perm != null && !sender.hasPermission(perm)) continue;

            var desc = entry.description();
            if (desc == null)
                desc = new FormattableMessage(FeatherMorphMain.getInstance(), "???");

            var command = displayCommand(entry.baseName());
            var permissionText = perm == null
                    ? HelpStrings.noPermissionRequired()
                    : new FormattableMessage(FeatherMorphMain.getInstance(), perm);

            var hover = HelpStrings.entryHover()
                    .resolve("command", command)
                    .resolve("description", desc)
                    .resolve("permission", permissionText)
                    .createComponent(locale);

            var line = HelpStrings.entryLine()
                    .resolve("command", command)
                    .resolve("description", desc)
                    .createComponent(locale)
                    .hoverEvent(HoverEvent.showText(hover))
                    .clickEvent(ClickEvent.suggestCommand(entry.suggestingCommand() + " "));

            lines.add(line);
        }

        if (lines.isEmpty()) return lines;

        var title = HelpStrings.categoryLine()
                .resolve("category", categoryName(section))
                .createComponent(locale);

        // 區段的補充說明放在分類標題的懸停提示裡，保持版面精簡
        var notes = section.getNotes();
        if (notes != null && !notes.isEmpty())
        {
            Component hover = HelpStrings.categoryNotesHover().createComponent(locale);
            for (var note : notes)
            {
                hover = hover.append(Component.newline())
                        .append(HelpStrings.noteLine().resolve("note", note).createComponent(locale));
            }

            title = title.hoverEvent(HoverEvent.showText(hover));
        }

        lines.add(0, title);
        return lines;
    }

    /**
     * 一頁式說明：依分類列出 sender 有權限使用的所有指令
     *
     * @param sender 要顯示給誰
     * @param only 只顯示這個分類；null 表示全部
     */
    private List<Component> constructHelpMessage(CommandSender sender, @org.jetbrains.annotations.Nullable Section only)
    {
        var list = new ObjectArrayList<Component>();
        var locale = MessageUtils.getLocale(sender);

        list.add(HelpStrings.pageHeader().createComponent(locale));

        for (var section : commandSections)
        {
            if (only != null && section != only) continue;
            list.addAll(constructSection(sender, section, locale));
        }

        list.add(HelpStrings.pageFooter().createComponent(locale));

        return list;
    }

    @Override
    public void registerAsChild(ArgumentBuilder<CommandSourceStack, ?> parentBuilder)
    {
        parentBuilder.then(
                Commands.literal(name())
                        .executes(this::executeNoArgs)
                        .then(
                                Commands.argument("section", StringArgumentType.greedyString())
                                        .suggests(this::suggestSection)
                                        .executes(this::executeWithArgs)
                        )
        );
    }

    private int executeNoArgs(CommandContext<CommandSourceStack> context)
    {
        var sender = context.getSource().getSender();

        for (var line : constructHelpMessage(sender, null))
            sender.sendMessage(line);

        return 1;
    }

    private int executeWithArgs(CommandContext<CommandSourceStack> context)
    {
        var sender = context.getSource().getSender();

        var sectionName = StringArgumentType.getString(context, "section");
        var section = commandSections.stream()
                .filter(s -> s.getCommandBaseName().equalsIgnoreCase(sectionName)).findFirst().orElse(null);

        if (section != null)
        {
            for (var line : constructHelpMessage(sender, section))
                sender.sendMessage(line);
        }
        else
        {
            MessageUtils.send(sender, HelpStrings.sectionNotFoundString());
        }

        return 1;
    }

    private CompletableFuture<Suggestions> suggestSection(CommandContext<CommandSourceStack> context, SuggestionsBuilder suggestionsBuilder)
    {
        var baseName = suggestionsBuilder.getRemainingLowerCase();

        var matchedSections = commandSections.stream()
                .filter(s -> s.getCommandBaseName().toLowerCase().startsWith(baseName.toLowerCase())).toList();

        for (var s : matchedSections)
            suggestionsBuilder.suggest(s.getCommandBaseName());

        return suggestionsBuilder.buildFuture();
    }

    @Override
    public FormattableMessage getHelpMessage()
    {
        return HelpStrings.helpDescription();
    }
}

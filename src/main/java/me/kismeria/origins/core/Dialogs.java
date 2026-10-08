package me.kismeria.origins.core;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.kismeria.origins.OriginsPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Race windows (vanilla Dialog API, no resource pack needed). */
public final class Dialogs {
    public static final Key WELCOME_OK = Key.key("smporigins", "welcome/ok");

    private final OriginsPlugin plugin;

    public Dialogs(OriginsPlugin plugin) {
        this.plugin = plugin;
    }

    private List<DialogBody> body(String locale, Origin o, String introKey, boolean loading) {
        Lang lang = plugin.lang();
        List<DialogBody> body = new ArrayList<>();
        if (introKey != null) body.add(DialogBody.plainMessage(lang.get(locale, introKey), 320));
        Component description = Lang.mm("<#c8c8c8><i>" + o.description(locale));
        if (o.icon() == null || o.icon().isAir()) {
            body.add(DialogBody.plainMessage(description, 260));
        } else {
            body.add(DialogBody.item(icon(o.icon(), loading))
                    .description(DialogBody.plainMessage(description, 260))
                    .showTooltip(false)
                    .build());
        }
        for (Ability a : o.abilities()) {
            Component line = Lang.mm((a.positive() ? "<#7CFC00>✚ " : "<#ff5555>✖ ") + "<white><b>" + o.abilityTitle(locale, a) + "</b><newline>")
                    .append(o.abilityText(locale, a).colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY));
            body.add(DialogBody.plainMessage(line, 320));
        }
        body.add(DialogBody.plainMessage(lang.get(locale, "dialog.difficulty", "dots", dots(o.impact())), 320));
        return body;
    }

    /**
     * На загрузке клиент ещё без тегов и части реестров: настоящая зачарованная книга роняет его
     * (вылет через 2 с после входа). Там показываем бумагу с моделью иконки — выглядит так же.
     */
    private static ItemStack icon(org.bukkit.Material material, boolean loading) {
        if (!loading) return new ItemStack(material);
        ItemStack stack = ItemStack.of(org.bukkit.Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, material.getKey());
        if (material == org.bukkit.Material.ENCHANTED_BOOK) {
            stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    static String dots(int impact) {
        String color = impact <= 1 ? "<green>" : impact == 2 ? "<yellow>" : "<red>";
        return color + "●".repeat(impact) + "<dark_gray>" + "●".repeat(3 - impact);
    }

    /** First meeting: shown while the player is still loading (or in game for late joins). */
    public Dialog welcome(String locale, Origin o, boolean loading) {
        Lang lang = plugin.lang();
        ActionButton ok = ActionButton.builder(lang.get(locale, "dialog.welcome-button")).width(200)
                .action(DialogAction.customClick(WELCOME_OK, null)).build();
        return Dialog.create(f -> f.empty()
                .base(DialogBase.builder(lang.get(locale, "dialog.welcome-title", "race", o.coloredName(locale)))
                        .canCloseWithEscape(!loading)
                        .afterAction(loading ? DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE : DialogBase.DialogAfterAction.CLOSE)
                        .body(body(locale, o, "dialog.welcome-intro", loading))
                        .build())
                .type(DialogType.notice(ok)));
    }

    /** /class or a click in the race list. */
    public Dialog info(String locale, Origin o, boolean own) {
        Lang lang = plugin.lang();
        ActionButton list = ActionButton.builder(lang.get(locale, "dialog.list-button")).width(150)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/origin list"))).build();
        ActionButton close = ActionButton.builder(lang.get(locale, "dialog.close-button")).width(150).build();
        String titleKey = own ? "dialog.own-title" : "dialog.info-title";
        return Dialog.create(f -> f.empty()
                .base(DialogBase.builder(lang.get(locale, titleKey, "race", o.coloredName(locale)))
                        .body(body(locale, o, null, false))
                        .build())
                .type(DialogType.confirmation(list, close)));
    }
}

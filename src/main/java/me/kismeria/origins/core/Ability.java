package me.kismeria.origins.core;

import java.util.Map;

/**
 * One line of an origin: a perk (positive) or a drawback.
 * Title and text live in lang files: origins.&lt;origin&gt;.abilities.&lt;id&gt;.title / .text,
 * {@code args} fill the &lt;placeholders&gt; of the text.
 */
public record Ability(String id, boolean positive, Map<String, Object> args) {
}

package com.bossmechanics.data;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Looks up a {@link Boss} by the title shown on a collection log page.
 *
 * The whole "does this page get a Boss Mechanics button?" question (docs/DECISIONS.md D15:
 * the button appears only on bosses that have data) reduces to this lookup returning
 * non-null, which is why it lives here as plain testable data code rather than inside the
 * widget-injection class.
 */
public final class BossPageIndex
{
	private static final Pattern TAG = Pattern.compile("<[^>]*>");

	private final Map<String, Boss> byPageTitle = new HashMap<>();

	public BossPageIndex(List<Boss> bosses)
	{
		for (Boss boss : bosses)
		{
			byPageTitle.put(normalize(boss.getName()), boss);
		}
	}

	/** @return the boss whose name matches this collection log page title, or null if none does. */
	public Boss forPageTitle(String pageTitle)
	{
		if (pageTitle == null)
		{
			return null;
		}

		String key = normalize(pageTitle);
		return key.isEmpty() ? null : byPageTitle.get(key);
	}

	/**
	 * Page titles come off a game widget, so they carry markup ("&lt;col=ff981f&gt;Vorkath&lt;/col&gt;")
	 * and whatever spacing the interface script used. Stripping tags and folding case/whitespace
	 * makes the match forgiving about presentation while staying exact about the name itself.
	 */
	private static String normalize(String value)
	{
		return TAG.matcher(value).replaceAll("")
			.replaceAll("\\s+", " ")
			.trim()
			.toLowerCase(Locale.ROOT);
	}
}

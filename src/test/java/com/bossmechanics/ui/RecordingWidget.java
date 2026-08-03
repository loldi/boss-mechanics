package com.bossmechanics.ui;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.widgets.Widget;

/**
 * A {@link Proxy}-backed stand-in for {@link Widget}, used to pin the two rebuild bugs (Bug A,
 * Bug B, docs/DECISIONS.md D22) down offline. Neither fix depends on real widget geometry, only
 * on which methods get called and in what order, which is exactly what a {@link Proxy} plus a
 * plain method-name log can answer without the injected client.
 *
 * <p>Every widget created off one root (via {@link #create} or the recorded {@code createChild})
 * shares one {@code allCalls} log, so a tree-wide invariant ("this method is never called on
 * anything in the tree") is one list to inspect. Each widget also keeps its own {@link #calls},
 * for invariants scoped to a single widget (call order on the root itself).
 *
 * <p>A setter that returns {@code Widget} (the fluent ones) returns the proxy itself, so
 * production code chaining a setter still works; everything else gets a harmless default.
 */
final class RecordingWidget implements InvocationHandler
{
	private final List<String> calls = new ArrayList<>();
	private final List<String> allCalls;
	private final List<Widget> children = new ArrayList<>();
	private final Map<String, Object> listeners = new HashMap<>();
	private final Map<String, Object[]> lastArgs = new HashMap<>();
	private final Map<String, List<Object[]>> allArgs = new HashMap<>();
	private final Map<String, Object> returning = new HashMap<>();

	private RecordingWidget(List<String> allCalls)
	{
		this.allCalls = allCalls;
	}

	/** A fresh widget with its own, unshared call log. */
	static Widget create()
	{
		return create(new ArrayList<>());
	}

	/** A fresh widget whose calls, and every {@code createChild}'s, land in {@code allCalls}. */
	static Widget create(List<String> allCalls)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
			new Class<?>[] { Widget.class }, new RecordingWidget(allCalls));
	}

	/** This widget's own calls, in order — for invariants scoped to one widget. */
	static List<String> callsOf(Widget widget)
	{
		return handlerOf(widget).calls;
	}

	/** Every child ever created on this widget via {@code createChild}, in creation order. */
	static List<Widget> childrenOf(Widget widget)
	{
		return handlerOf(widget).children;
	}

	/** The object passed to a {@code setOnXListener(Object...)} call, or null if never set. */
	static Object listenerOf(Widget widget, String setterName)
	{
		return handlerOf(widget).listeners.get(setterName);
	}

	/**
	 * The args of the most recent call to {@code methodName} on this widget, or null if it was
	 * never called. Additive alongside {@link #listenerOf}/{@link #callsOf}: those tests are
	 * name-only and stay untouched by this separate map.
	 */
	static Object[] lastArgsOf(Widget widget, String methodName)
	{
		return handlerOf(widget).lastArgs.get(methodName);
	}

	/**
	 * Every call to {@code methodName} on this widget, in order, args included. Additive alongside
	 * {@link #lastArgsOf}: that map only ever keeps the most recent call, which cannot tell "this
	 * widget's animation was set to X once" apart from "...set to X, then later to Y" — exactly the
	 * distinction the pool-widget lifetime invariant needs.
	 */
	static List<Object[]> allArgsOf(Widget widget, String methodName)
	{
		return handlerOf(widget).allArgs.getOrDefault(methodName, new ArrayList<>());
	}

	/**
	 * Pins a getter's return value, e.g. {@code returning(host, "getWidth", 765)}. Additive
	 * alongside {@link #lastArgsOf}/{@link #allArgsOf}: those record what production code *sent*,
	 * this controls what a fake widget *answers* -- issue #48's drag clamp needs real widths,
	 * heights and relative offsets rather than every getter's harmless zero default.
	 */
	static void returning(Widget widget, String methodName, Object value)
	{
		handlerOf(widget).returning.put(methodName, value);
	}

	/** A harmless stand-in for a return type we don't need to model precisely. */
	static Object defaultFor(Class<?> type)
	{
		if (type == boolean.class)
		{
			return false;
		}
		if (type == int.class)
		{
			return 0;
		}
		if (type == Widget[].class)
		{
			return new Widget[0];
		}
		return null;
	}

	private static RecordingWidget handlerOf(Widget widget)
	{
		return (RecordingWidget) Proxy.getInvocationHandler(widget);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
	{
		String name = method.getName();
		calls.add(name);
		allCalls.add(name);
		lastArgs.put(name, args);
		allArgs.computeIfAbsent(name, key -> new ArrayList<>()).add(args);

		// Every listener setter is a varargs Object..., so the single formal parameter Proxy
		// hands us is itself the caller's varargs array — the callback is its first element.
		if (name.startsWith("setOn") && name.endsWith("Listener")
			&& args != null && args.length == 1 && args[0] instanceof Object[])
		{
			Object[] varargs = (Object[]) args[0];
			if (varargs.length > 0)
			{
				listeners.put(name, varargs[0]);
			}
		}

		if (returning.containsKey(name))
		{
			return returning.get(name);
		}

		if (name.equals("createChild"))
		{
			Widget child = create(allCalls);
			children.add(child);
			return child;
		}

		Class<?> returnType = method.getReturnType();
		// Only a fluent setter (setX(...) returning Widget for chaining) defaults to the proxy
		// itself. A real getter that happens to return Widget -- getParent(), notably -- defaults
		// to null like every other unstubbed getter would, via defaultFor: without this split,
		// getParent() returned the same widget forever and WindowPlacement.offsetInRoot's
		// parent-chain walk (issue #48) never terminated.
		if (name.startsWith("set") && returnType == Widget.class)
		{
			return proxy;
		}
		return defaultFor(returnType);
	}
}

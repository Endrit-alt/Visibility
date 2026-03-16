package com.visibilityenhancer;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
		name = "Visibility Enhancer",
		description = "Adjusts teammate opacity and silhouettes for raids.",
		tags = {"raid", "opacity", "ghost", "silhouette"}
)
public class VisibilityEnhancer extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private VisibilityEnhancerConfig config;

	private final Map<Model, SavedModelState> selfCache = new WeakHashMap<>();

	@Override
	protected void shutDown() throws Exception
	{
		selfCache.clear();
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		Model model = event.getPlayer().getModel();
		if (model != null)
		{
			selfCache.remove(model);
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		handleSelf(local);

		for (Player player : client.getPlayers())
		{
			if (player == null || player == local)
			{
				continue;
			}

			if (config.ignoreFriends() && (player.isFriend() || client.isFriended(player.getName(), false)))
			{
				restoreOtherOpacity(player);
				continue;
			}

			if (local.getWorldLocation().distanceTo(player.getWorldLocation()) <= config.proximityRange())
			{
				applyOtherOpacity(player, config.playerOpacity());
			}
			else
			{
				restoreOtherOpacity(player);
			}
		}
	}

	private void handleSelf(Player local)
	{
		Model model = local.getModel();
		if (model == null)
		{
			return;
		}

		if (!selfCache.containsKey(model))
		{
			selfCache.put(model, new SavedModelState(
					copy(model.getFaceTransparencies()),
					copy(model.getFaceColors1()),
					copy(model.getFaceColors2()),
					copy(model.getFaceColors3()),
					copy(model.getFaceRenderPriorities()),
					copy(model.getFaceTextures())
			));
		}

		applyModelOpacity(model, config.selfOpacity());

		if (config.selfSilhouette())
		{
			applyModelSilhouette(model, config.silhouetteColor());
		}
		else
		{
			restoreSelfColors(model);
		}
	}

	private void applyOtherOpacity(Player player, int opacityPercent)
	{
		Model model = player.getModel();
		if (model == null)
		{
			return;
		}

		model = client.applyTransformations(model, null, 0, null, 0);

		byte[] trans = model.getFaceTransparencies();
		if (trans != null)
		{
			int alpha = (int) ((100 - opacityPercent) * 2.55);
			Arrays.fill(trans, (byte) Math.max(0, Math.min(255, alpha)));
		}
	}

	private void restoreOtherOpacity(Player player)
	{
		Model model = player.getModel();
		if (model == null)
		{
			return;
		}

		byte[] trans = model.getFaceTransparencies();
		if (trans != null && trans.length > 0 && (trans[0] & 0xFF) != 0)
		{
			Arrays.fill(trans, (byte) 0);
		}
	}

	private void applyModelOpacity(Model model, int opacityPercent)
	{
		byte[] trans = model.getFaceTransparencies();
		if (trans == null)
		{
			return;
		}

		int alpha = (int) ((100 - opacityPercent) * 2.55);
		Arrays.fill(trans, (byte) Math.max(0, Math.min(255, alpha)));
	}

	private void applyModelSilhouette(Model model, Color color)
	{
		int hsl = colorToHSL(color);

		if (model.getFaceColors1() != null)
		{
			Arrays.fill(model.getFaceColors1(), hsl);
		}
		if (model.getFaceColors2() != null)
		{
			Arrays.fill(model.getFaceColors2(), hsl);
		}
		if (model.getFaceColors3() != null)
		{
			Arrays.fill(model.getFaceColors3(), hsl);
		}
		if (model.getFaceRenderPriorities() != null)
		{
			Arrays.fill(model.getFaceRenderPriorities(), (byte) 10);
		}
		if (model.getFaceTextures() != null)
		{
			Arrays.fill(model.getFaceTextures(), (short) -1);
		}
	}

	private void restoreSelfColors(Model model)
	{
		SavedModelState state = selfCache.get(model);
		if (state == null)
		{
			return;
		}

		if (model.getFaceColors1() != null && state.c1 != null && model.getFaceColors1().length == state.c1.length)
		{
			System.arraycopy(state.c1, 0, model.getFaceColors1(), 0, state.c1.length);
		}
		if (model.getFaceColors2() != null && state.c2 != null && model.getFaceColors2().length == state.c2.length)
		{
			System.arraycopy(state.c2, 0, model.getFaceColors2(), 0, state.c2.length);
		}
		if (model.getFaceColors3() != null && state.c3 != null && model.getFaceColors3().length == state.c3.length)
		{
			System.arraycopy(state.c3, 0, model.getFaceColors3(), 0, state.c3.length);
		}
		if (model.getFaceRenderPriorities() != null && state.priorities != null
				&& model.getFaceRenderPriorities().length == state.priorities.length)
		{
			System.arraycopy(state.priorities, 0, model.getFaceRenderPriorities(), 0, state.priorities.length);
		}
		if (model.getFaceTextures() != null && state.textures != null && model.getFaceTextures().length == state.textures.length)
		{
			System.arraycopy(state.textures, 0, model.getFaceTextures(), 0, state.textures.length);
		}
	}

	private int colorToHSL(Color color)
	{
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		return ((int) (hsb[0] * 63) << 10) | ((int) (hsb[1] * 7) << 7) | (int) (hsb[2] * 127);
	}

	private byte[] copy(byte[] a)
	{
		return a == null ? null : Arrays.copyOf(a, a.length);
	}

	private int[] copy(int[] a)
	{
		return a == null ? null : Arrays.copyOf(a, a.length);
	}

	private short[] copy(short[] a)
	{
		return a == null ? null : Arrays.copyOf(a, a.length);
	}

	private static class SavedModelState
	{
		final byte[] trans;
		final int[] c1;
		final int[] c2;
		final int[] c3;
		final byte[] priorities;
		final short[] textures;

		SavedModelState(byte[] t, int[] color1, int[] color2, int[] color3, byte[] p, short[] tex)
		{
			trans = t;
			c1 = color1;
			c2 = color2;
			c3 = color3;
			priorities = p;
			textures = tex;
		}
	}

	@Provides
	VisibilityEnhancerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VisibilityEnhancerConfig.class);
	}
}
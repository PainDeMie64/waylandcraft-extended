package dev.evvie.waylandcraft.debug;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.opengl.GL33;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.render.BufferTexture;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class TextureDebug {

	private static final AtomicInteger NEXT_BUFFER_ID = new AtomicInteger(1);
	private static final AtomicInteger NEXT_FRAMEBUFFER_ID = new AtomicInteger(1);
	private static final IdentityHashMap<BufferTexture, BufferState> BUFFERS = new IdentityHashMap<BufferTexture, BufferState>();
	private static final IdentityHashMap<WindowFramebuffer, FramebufferState> FRAMEBUFFERS = new IdentityHashMap<WindowFramebuffer, FramebufferState>();
	private static final Map<Identifier, FramebufferState> FRAMEBUFFERS_BY_LOCATION = new HashMap<Identifier, FramebufferState>();
	private static long frame = 0;

	private TextureDebug() {
	}

	public static boolean enabled() {
		return WaylandCraft.DEBUG_TEXTURES;
	}

	public static long frame() {
		return frame;
	}

	public static void nextFrame() {
		frame++;
	}

	public static int nextBufferId() {
		return NEXT_BUFFER_ID.getAndIncrement();
	}

	public static int nextFramebufferId() {
		return NEXT_FRAMEBUFFER_ID.getAndIncrement();
	}

	public static void bufferCreated(BufferTexture buffer, String kind, long dmabufHandle) {
		synchronized(BUFFERS) {
			BUFFERS.put(buffer, new BufferState(buffer.debugId(), kind, buffer.id, identity(buffer.textureView), identity(buffer.textureView.texture()), dmabufHandle, buffer.width, buffer.height, buffer.format));
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC texture frame={} create buffer#{} kind={} gl={} valid={} view={} gpuTexture={} dmabuf={} size={}x{} format={}", frame(), buffer.debugId(), kind, buffer.id, glIsTextureSafe(buffer.id), identity(buffer.textureView), identity(buffer.textureView.texture()), dmabufHandle, buffer.width, buffer.height, buffer.format);
		}
	}

	public static void bufferRetagged(BufferTexture buffer, String kind, long dmabufHandle) {
		synchronized(BUFFERS) {
			BufferState state = BUFFERS.get(buffer);
			if(state != null) {
				state.kind = kind;
				state.dmabufHandle = dmabufHandle;
			}
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC texture frame={} tag buffer#{} kind={} gl={} dmabuf={} size={}x{}", frame(), buffer.debugId(), kind, buffer.id, dmabufHandle, buffer.width, buffer.height);
		}
	}

	public static void bufferRelease(BufferTexture buffer, String reason, boolean deletesGlTexture) {
		synchronized(BUFFERS) {
			BufferState state = BUFFERS.get(buffer);
			if(state != null && deletesGlTexture) state.released = true;
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC texture frame={} release buffer#{} kind={} gl={} validBefore={} deletesGl={} reason={}", frame(), buffer.debugId(), bufferKind(buffer), buffer.id, glIsTextureSafe(buffer.id), deletesGlTexture, reason);
		}
	}

	public static void bufferFree(BufferTexture buffer, String reason) {
		synchronized(BUFFERS) {
			BufferState state = BUFFERS.get(buffer);
			if(state != null) state.freed = true;
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC dmabuf frame={} free buffer#{} kind={} gl={} validBefore={} dmabuf={} reason={}", frame(), buffer.debugId(), bufferKind(buffer), buffer.id, glIsTextureSafe(buffer.id), dmabufHandle(buffer), reason);
		}
	}

	public static void surfaceAttach(WLCSurface surface, String reason, BufferTexture oldBuffer, BufferTexture newBuffer, int oldLiveRefs) {
		if(!enabled()) return;
		WaylandCraft.LOGGER.info("WLC surface texture frame={} attach surface={} reason={} old={} oldRefs={} new={} size={}x{}", frame(), surface.getDebugHandle(), reason, describeBuffer(oldBuffer), oldLiveRefs, describeBuffer(newBuffer), surface.width(), surface.height());
	}

	public static void surfaceRemove(WLCSurface surface, BufferTexture oldBuffer, int oldLiveRefs) {
		if(!enabled()) return;
		WaylandCraft.LOGGER.info("WLC surface texture frame={} remove surface={} old={} oldRefs={}", frame(), surface.getDebugHandle(), describeBuffer(oldBuffer), oldLiveRefs);
	}

	public static void dmabufDecision(BufferTexture buffer, boolean nativePresent, int javaRefs, String surfaces) {
		if(!enabled()) return;
		WaylandCraft.LOGGER.info("WLC dmabuf frame={} decision buffer={} nativePresent={} javaRefs={} surfaces={} valid={} released={} freed={}", frame(), describeBuffer(buffer), nativePresent, javaRefs, surfaces, glIsTextureSafe(buffer.id), isReleased(buffer), isFreed(buffer));
	}

	public static void framebufferCreated(WindowFramebuffer framebuffer, WLCSurface surface) {
		synchronized(FRAMEBUFFERS) {
			FRAMEBUFFERS.put(framebuffer, new FramebufferState(framebuffer.debugId(), surface.getDebugHandle()));
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC framebuffer frame={} create fb#{} surface={}", frame(), framebuffer.debugId(), surface.getDebugHandle());
		}
	}

	public static void framebufferRegistered(WindowFramebuffer framebuffer, Identifier location, int width, int height, int xoff, int yoff, int generation, GpuTextureView textureView) {
		FramebufferState state = framebufferState(framebuffer);
		state.location = location;
		state.width = width;
		state.height = height;
		state.xoff = xoff;
		state.yoff = yoff;
		state.generation = generation;
		state.viewIdentity = identity(textureView);
		state.gpuTextureIdentity = textureView == null ? "null" : identity(textureView.texture());
		state.glId = glId(textureView);
		state.status = FramebufferStatus.LIVE;
		synchronized(FRAMEBUFFERS_BY_LOCATION) {
			FRAMEBUFFERS_BY_LOCATION.put(location, state);
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC framebuffer frame={} register fb#{} location={} surface={} generation={} gl={} valid={} view={} gpuTexture={} size={}x{} offset={}x{}", frame(), state.id, location, state.surfaceHandle, generation, state.glId, state.glId == 0 ? "unknown" : glIsTextureSafe(state.glId), state.viewIdentity, state.gpuTextureIdentity, width, height, xoff, yoff);
		}
	}

	public static void framebufferRetired(WindowFramebuffer framebuffer, Identifier location, int width, int height, int generation) {
		FramebufferState state = framebufferState(framebuffer);
		state.location = location;
		state.width = width;
		state.height = height;
		state.generation = generation;
		state.status = FramebufferStatus.RETIRED;
		synchronized(FRAMEBUFFERS_BY_LOCATION) {
			FRAMEBUFFERS_BY_LOCATION.put(location, state);
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC framebuffer frame={} retire fb#{} location={} surface={} generation={} gl={} valid={} size={}x{}", frame(), state.id, location, state.surfaceHandle, generation, state.glId, state.glId == 0 ? "unknown" : glIsTextureSafe(state.glId), width, height);
		}
	}

	public static void framebufferRetireDestroy(int framebufferDebugId, Identifier location, int width, int height, int age) {
		FramebufferState state;
		synchronized(FRAMEBUFFERS_BY_LOCATION) {
			state = FRAMEBUFFERS_BY_LOCATION.get(location);
			if(state != null) state.status = FramebufferStatus.DESTROYED;
		}
		if(enabled()) {
			WaylandCraft.LOGGER.info("WLC framebuffer frame={} retire-destroy fb#{} location={} trackedFb={} gl={} valid={} size={}x{} age={}", frame(), framebufferDebugId, location, state == null ? "missing" : state.id, state == null ? 0 : state.glId, state == null || state.glId == 0 ? "unknown" : glIsTextureSafe(state.glId), width, height, age);
		}
	}

	public static void framebufferPresented(WindowFramebuffer framebuffer, String owner) {
		if(!enabled()) return;
		FramebufferState state = framebufferState(framebuffer);
		boolean invalidStatus = state.status != FramebufferStatus.LIVE;
		boolean invalidGl = state.glId != 0 && !glIsTextureSafe(state.glId);
		if(invalidStatus || invalidGl) {
			WaylandCraft.LOGGER.info("WLC framebuffer frame={} present-invalid owner={} fb#{} location={} status={} gl={} valid={} size={}x{} view={} gpuTexture={}", frame(), owner, state.id, state.location, state.status, state.glId, state.glId == 0 ? "unknown" : glIsTextureSafe(state.glId), state.width, state.height, state.viewIdentity, state.gpuTextureIdentity);
		}
	}

	public static void textureManagerRegister(Identifier location, AbstractTexture oldTexture, AbstractTexture newTexture) {
		if(!enabled()) return;
		if(!isWaylandLocation(location)) return;
		WaylandCraft.LOGGER.info("WLC TextureManager frame={} register location={} old={} new={} newIsGlobalMissing={} tracked={}", frame(), location, describeTexture(oldTexture), describeTexture(newTexture), isGlobalMissingTexture(newTexture), describeLocation(location));
	}

	public static void textureManagerRelease(Identifier location, AbstractTexture oldTexture) {
		if(!enabled()) return;
		if(!isWaylandLocation(location)) return;
		WaylandCraft.LOGGER.info("WLC TextureManager frame={} release location={} old={} tracked={}", frame(), location, describeTexture(oldTexture), describeLocation(location));
	}

	public static void beforeBindBuffer(BufferTexture buffer, String owner) {
		if(!enabled()) return;
		boolean invalidGl = !glIsTextureSafe(buffer.id);
		boolean badState = isReleased(buffer) || isFreed(buffer);
		if(invalidGl || badState) {
			WaylandCraft.LOGGER.info("WLC texture frame={} bind-invalid owner={} buffer={} valid={} released={} freed={}", frame(), owner, describeBuffer(buffer), !invalidGl, isReleased(buffer), isFreed(buffer));
		}
	}

	public static void beforePresentLocation(Identifier location, String owner) {
		if(!enabled()) return;
		FramebufferState state;
		synchronized(FRAMEBUFFERS_BY_LOCATION) {
			state = FRAMEBUFFERS_BY_LOCATION.get(location);
		}
		if(state == null) {
			WaylandCraft.LOGGER.info("WLC framebuffer frame={} present-untracked owner={} location={}", frame(), owner, location);
			return;
		}
		boolean invalidStatus = state.status != FramebufferStatus.LIVE;
		boolean invalidGl = state.glId != 0 && !glIsTextureSafe(state.glId);
		if(invalidStatus || invalidGl) {
			WaylandCraft.LOGGER.info("WLC framebuffer frame={} present-location-invalid owner={} location={} fb#{} status={} gl={} valid={} size={}x{}", frame(), owner, location, state.id, state.status, state.glId, state.glId == 0 ? "unknown" : glIsTextureSafe(state.glId), state.width, state.height);
		}
	}

	public static String describeBuffer(BufferTexture buffer) {
		if(buffer == null) return "null";
		return "buffer#" + buffer.debugId() + "[" + bufferKind(buffer) + " gl=" + buffer.id + " dmabuf=" + dmabufHandle(buffer) + " size=" + buffer.width + "x" + buffer.height + " view=" + identity(buffer.textureView) + " gpuTexture=" + identity(buffer.textureView.texture()) + "]";
	}

	public static boolean isReleased(BufferTexture buffer) {
		synchronized(BUFFERS) {
			BufferState state = BUFFERS.get(buffer);
			return state != null && state.released;
		}
	}

	public static boolean isFreed(BufferTexture buffer) {
		synchronized(BUFFERS) {
			BufferState state = BUFFERS.get(buffer);
			return state != null && state.freed;
		}
	}

	private static String bufferKind(BufferTexture buffer) {
		synchronized(BUFFERS) {
			BufferState state = BUFFERS.get(buffer);
			return state == null ? buffer.getClass().getSimpleName() : state.kind;
		}
	}

	private static long dmabufHandle(BufferTexture buffer) {
		synchronized(BUFFERS) {
			BufferState state = BUFFERS.get(buffer);
			return state == null ? 0 : state.dmabufHandle;
		}
	}

	private static FramebufferState framebufferState(WindowFramebuffer framebuffer) {
		synchronized(FRAMEBUFFERS) {
			return FRAMEBUFFERS.computeIfAbsent(framebuffer, (fb) -> new FramebufferState(fb.debugId(), fb.surfaceTree.getDebugHandle()));
		}
	}

	private static String describeLocation(Identifier location) {
		synchronized(FRAMEBUFFERS_BY_LOCATION) {
			FramebufferState state = FRAMEBUFFERS_BY_LOCATION.get(location);
			if(state == null) return "untracked";
			return "fb#" + state.id + " status=" + state.status + " gl=" + state.glId + " size=" + state.width + "x" + state.height;
		}
	}

	private static String describeTexture(AbstractTexture texture) {
		if(texture == null) return "null";
		GpuTexture gpuTexture = texture.getTexture();
		int glId = gpuTexture instanceof GlTexture glTexture ? glTexture.glId() : 0;
		return texture.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(texture)) + " gpuTexture=" + (gpuTexture == null ? "null" : gpuTexture.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(gpuTexture))) + " gl=" + glId + " valid=" + (glId == 0 ? "unknown" : glIsTextureSafe(glId));
	}

	private static boolean isGlobalMissingTexture(AbstractTexture texture) {
		if(texture == null) return false;
		try {
			return Minecraft.getInstance().getTextureManager().getTexture(MissingTextureAtlasSprite.getLocation()) == texture;
		}
		catch(Throwable ignored) {
			return false;
		}
	}

	private static boolean isWaylandLocation(Identifier location) {
		return WaylandCraft.MOD_ID.equals(location.getNamespace());
	}

	private static int glId(GpuTextureView textureView) {
		if(textureView == null) return 0;
		GpuTexture texture = textureView.texture();
		if(texture instanceof GlTexture glTexture) return glTexture.glId();
		return 0;
	}

	private static boolean glIsTextureSafe(int glId) {
		if(glId == 0) return false;
		try {
			return GL33.glIsTexture(glId);
		}
		catch(Throwable ignored) {
			return false;
		}
	}

	private static String identity(Object object) {
		if(object == null) return "null";
		return object.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(object));
	}

	private enum FramebufferStatus {
		LIVE,
		RETIRED,
		DESTROYED
	}

	private static final class BufferState {
		public final int id;
		public String kind;
		public final int glId;
		public final String viewIdentity;
		public final String gpuTextureIdentity;
		public long dmabufHandle;
		public final int width;
		public final int height;
		public final int format;
		public boolean released = false;
		public boolean freed = false;

		private BufferState(int id, String kind, int glId, String viewIdentity, String gpuTextureIdentity, long dmabufHandle, int width, int height, int format) {
			this.id = id;
			this.kind = kind;
			this.glId = glId;
			this.viewIdentity = viewIdentity;
			this.gpuTextureIdentity = gpuTextureIdentity;
			this.dmabufHandle = dmabufHandle;
			this.width = width;
			this.height = height;
			this.format = format;
		}
	}

	private static final class FramebufferState {
		public final int id;
		public final long surfaceHandle;
		public Identifier location = null;
		public int width = 0;
		public int height = 0;
		public int xoff = 0;
		public int yoff = 0;
		public int generation = 0;
		public String viewIdentity = "null";
		public String gpuTextureIdentity = "null";
		public int glId = 0;
		public FramebufferStatus status = FramebufferStatus.LIVE;

		private FramebufferState(int id, long surfaceHandle) {
			this.id = id;
			this.surfaceHandle = surfaceHandle;
		}
	}

}

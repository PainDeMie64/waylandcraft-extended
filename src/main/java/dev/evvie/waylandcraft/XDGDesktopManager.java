package dev.evvie.waylandcraft;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

import javax.imageio.ImageIO;

import org.lwjgl.system.jni.JNINativeInterface;

public class XDGDesktopManager {
	
	private HashMap<String, String> nameCache = new HashMap<String, String>();
	private HashMap<String, BufferTexture> iconCache = new HashMap<String, BufferTexture>();
	
	public String getName(String appID) {
		if(appID == null) return null;
		
		if(nameCache.containsKey(appID)) {
			return nameCache.get(appID);
		}
		
		String name = WaylandCraft.instance.bridge.resolveName(appID);
		nameCache.put(appID, name);
		return name;
	}
	
	public BufferTexture getIcon(String appID) {
		if(appID == null) return null;
		
		if(iconCache.containsKey(appID)) {
			return iconCache.get(appID);
		}
		
		BufferTexture icon = tryRetrieveIcon(appID);
		iconCache.put(appID, icon);
		return icon;
	}
	
	private BufferTexture tryRetrieveIcon(String appID) {
		try {
			return retrieveIcon(appID);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private String getExtension(File file) {
		String path = file.getAbsolutePath();
		int idx = path.lastIndexOf('.');
		if(idx < 0 || idx >= path.length() - 1) return "";
		
		return path.substring(idx + 1);
	}
	
	private BufferTexture retrieveIcon(String appID) throws IOException {
		String iconPath = WaylandCraft.instance.bridge.resolveIconPath(appID);
		System.out.println("Found icon path: " + iconPath);
		
		if(iconPath == null) return null;
		
		File iconFile = new File(iconPath);
		
		/* This "file type check" is valid because according to the Icon Theme Specification
		 * the extension has to be one of ".png", ".xpm" and ".svg" (lowercase) and the extension
		 * signals what type of file we should expect.
		 */
		if(!getExtension(iconFile).equals("png")) {
			System.err.println("Icon is not PNG!");
			return null;
		}
		
		BufferedImage image = ImageIO.read(iconFile);
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		
		ByteBuffer buf = ByteBuffer.allocateDirect(pixels.length * 4);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		for(int pixel : pixels) {
			buf.putInt(pixel);
	    }
		buf.flip();
		
		BufferTexture texture = new BufferTexture.ShmBufferTexture(JNINativeInterface.GetDirectBufferAddress(buf), width, height, BufferTexture.FORMAT_ARGB8888);
		return texture;
	}
	
}

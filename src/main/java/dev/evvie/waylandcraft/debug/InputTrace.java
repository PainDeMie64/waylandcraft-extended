package dev.evvie.waylandcraft.debug;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import dev.evvie.waylandcraft.WaylandCraft;

public final class InputTrace {
	public static final boolean ENABLED = envFlagEnabled("WAYLANDCRAFT_INPUT_TRACE");

	private static final AtomicLong NEXT_ID = new AtomicLong(1);
	private static final ThreadLocal<Long> CURRENT_ID = ThreadLocal.withInitial(() -> 0L);
	private static final Object LOCK = new Object();
	private static final long PID = ProcessHandle.current().pid();
	private static final Path PATH = Path.of("/tmp", "waylandcraft-input-trace-" + PID + "-" + Instant.now().toEpochMilli() + ".java.jsonl");
	private static final Path NATIVE_PATH = Path.of(PATH.toString().replace(".java.jsonl", ".native.jsonl"));
	private static BufferedWriter writer;

	private InputTrace() {}

	public static Path path() {
		return PATH;
	}

	public static Path nativePath() {
		return NATIVE_PATH;
	}

	public static long current() {
		return CURRENT_ID.get();
	}

	public static long begin(String eventType, String fields) {
		if(!ENABLED) return 0L;
		long id = NEXT_ID.getAndIncrement();
		CURRENT_ID.set(id);
		event("java", eventType, fields);
		return id;
	}

	public static long currentOrBegin(String eventType, String fields) {
		if(!ENABLED) return 0L;
		long id = CURRENT_ID.get();
		if(id != 0) return id;
		return begin(eventType, fields);
	}

	public static void clear() {
		if(!ENABLED) return;
		CURRENT_ID.set(0L);
	}

	public static void event(String layer, String eventType, String fields) {
		if(!ENABLED) return;
		writeLine(layer, eventType, fields == null || fields.isBlank() ? "" : fields);
	}

	public static void info(String eventType, String fields) {
		event("java", eventType, fields);
	}

	public static String s(String value) {
		if(value == null) return "null";
		return "\"" + escape(value) + "\"";
	}

	private static void writeLine(String layer, String eventType, String fields) {
		synchronized(LOCK) {
			try {
				if(writer == null) {
					writer = Files.newBufferedWriter(PATH, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
					WaylandCraft.LOGGER.info("WLC input trace java={}", PATH);
					WaylandCraft.LOGGER.info("WLC input trace native={}", NATIVE_PATH);
				}

				writer.write("{\"ts_nanos\":");
				writer.write(Long.toString(System.nanoTime()));
				writer.write(",\"wall_millis\":");
				writer.write(Long.toString(System.currentTimeMillis()));
				writer.write(",\"pid\":");
				writer.write(Long.toString(PID));
				writer.write(",\"jvm\":\"");
				writer.write(escape(ManagementFactory.getRuntimeMXBean().getName()));
				writer.write("\",\"thread\":\"");
				writer.write(escape(Thread.currentThread().getName()));
				writer.write("\",\"trace\":");
				writer.write(Long.toString(current()));
				writer.write(",\"layer\":\"");
				writer.write(escape(layer));
				writer.write("\",\"event_type\":\"");
				writer.write(escape(eventType));
				writer.write("\"");
				if(!fields.isBlank()) {
					writer.write(",");
					writer.write(fields);
				}
				writer.write("}\n");
				writer.flush();
			} catch(IOException e) {
				WaylandCraft.LOGGER.warn("Failed to write WaylandCraft input trace", e);
			}
		}
	}

	private static String escape(String value) {
		StringBuilder out = new StringBuilder(value.length() + 8);
		for(int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch(c) {
				case '\\' -> out.append("\\\\");
				case '"' -> out.append("\\\"");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if(c < 0x20) out.append(String.format("\\u%04x", (int)c));
					else out.append(c);
				}
			}
		}
		return out.toString();
	}

	private static boolean envFlagEnabled(String name) {
		String value = System.getenv(name);
		return value != null && !value.isBlank() && !value.equals("0") && !value.equalsIgnoreCase("false") && !value.equalsIgnoreCase("no");
	}
}

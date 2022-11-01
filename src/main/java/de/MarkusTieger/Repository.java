package de.MarkusTieger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import de.MarkusTieger.obj.PackagesObj;

public class Repository {

	private static final Gson GSON = new GsonBuilder().create();
	
	public static void main(String[] args) throws IOException, InterruptedException {
		System.setProperty("code-name", args[0]);
		
		PackagesObj obj = null;
		try (InputStream in = Repository.class.getResourceAsStream("/packages.json")) {
			byte[] data = in.readAllBytes();
			obj = GSON.fromJson(new String(data, StandardCharsets.UTF_8), PackagesObj.class);
		}
		
		File tmp = new File("tmp");
		if(!tmp.exists()) tmp.mkdirs();
		
		for(String d : obj.direct) {
			String filename = UUID.randomUUID() + ".deb";
			System.out.println("Downloading " + d + " to tmp/direct/" + filename + " ...");
			URL url = new URL(d);
			File target = new File(tmp, filename);
			ProcessBuilder builder = new ProcessBuilder("curl", url.toString(), "-L", "-o", target.getAbsolutePath());
			builder.inheritIO();
			Process p = builder.start();
			int exit = p.waitFor();
			if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
			if(!target.exists()) throw new RuntimeException("Deb File does not exists.");
			
			System.out.println("Installing to Repository...");
			
			installDeb(target);
			
			System.out.println("Finished current.");
			System.out.println();
		}
		
	}

	private static void installDeb(File target) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder("reprepro", "includedeb", System.getProperty("code-name"), target.getAbsolutePath());
		builder.directory(new File("tiger-os"));
		builder.inheritIO();
		Process p = builder.start();
		int exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
	}
	
}

package de.MarkusTieger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import de.MarkusTieger.obj.AdditionalObj;
import de.MarkusTieger.obj.GithubIdentifierObj;
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
		
		System.out.println("Connecting to Github...");
		
		GitHub github = GitHub.connectAnonymously();
		
		for(GithubIdentifierObj identifier : obj.github) {
			
			GHRepository repo = github.getRepository(identifier.group + "/" + identifier.identifier);
			GHRelease release = repo.getLatestRelease();
			for(GHAsset asset : release.listAssets()) {
				if(asset.getName().toLowerCase().contains(identifier.contains)) {
					System.out.println("Adding " + asset.getName() + " from " + identifier.group + "/" + identifier.identifier + " to download list.");
					obj.direct.put(asset.getBrowserDownloadUrl(), identifier);
					System.out.println("Finished current.");
					System.out.println();
				}
			}
		}
		
		for(Entry<String, AdditionalObj> e : obj.direct.entrySet()) {
			String d = e.getKey();
			
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
			
			installDeb(target, e.getValue());
			
			System.out.println("Finished current.");
			System.out.println();
		}
		
		
		ProcessBuilder builder = new ProcessBuilder("rm", "-rf", tmp.getAbsolutePath());
		Process p = builder.start();
		int exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
		if(tmp.exists()) throw new RuntimeException("Temp. Folder does exists.");
		
	}

	private static void installDeb(File target, AdditionalObj additional) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder("reprepro", "-C", "main", "-S", additional.section, "-P", additional.priority, "includedeb", System.getProperty("code-name"), target.getAbsolutePath());
		builder.directory(new File("tiger-os"));
		builder.inheritIO();
		Process p = builder.start();
		int exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
	}
	
}

package de.MarkusTieger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.tools.FileObject;

import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.MarkusTieger.obj.AdditionalObj;
import de.MarkusTieger.obj.GithubIdentifierObj;
import de.MarkusTieger.obj.KeyServerObj;
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
		
		Map<File, AdditionalObj> map = new HashMap<>();
		
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
			
			map.put(target, e.getValue());
			
			System.out.println("Finished current.");
			System.out.println();
		}
		
		
		
		String image = "tigeros-repository";
		String docker_uuid = UUID.randomUUID().toString();
		
		List<String> command = new ArrayList<>();
		// "/usr/bin/sudo", "/usr/bin/docker", "run", image, "apt-get install -y --download-only " + packages
		/*command.add("/usr/bin/sudo");
		command.add("/usr/bin/docker");
		command.add("run");
		command.add(image);*/
		command.add("apt-get");
		command.add("install");
		command.add("-y");
		command.add("--download-only");
		
		for(String pack : obj.packages) command.add(pack);
		
		System.out.println("Building Docker-Image...");
		
		String dockerfile = """
				
				FROM ubuntu:latest

				LABEL maintainer="markustieger@gmail.com"

				RUN apt-get update && apt-get upgrade -y && apt-get install -y software-properties-common && rm -rf /var/lib/apt/lists/*
				
				RUN rm -rf /var/cache/apt/archives
				RUN mkdir /var/cache/apt/archives
				
				""";
		
		
		
		System.out.println("Adding Repositories...");
		
		/*builder = new ProcessBuilder("/usr/bin/sudo", "/usr/bin/docker", "run", image, "echo", "", ">>", "/etc/apt/sources.list");
		builder.inheritIO();
		p = builder.start();
		exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);*/
		
		for(KeyServerObj keyserver : obj.keys_keyserver) {
			dockerfile += "RUN apt-key adv --keyserver \"" + keyserver.keyserver + "\" --recv-keys \"" + keyserver.recv_keys + "\"\n";
		}
		
		for(String repo : obj.repositories) {
			
			dockerfile += "RUN add-apt-repository \"" + repo + "\"\n";
		}
		
		dockerfile += "RUN apt-get update\n";
		
		System.out.println("Downloading Packages...");
		
		dockerfile += "RUN ";
		for(String cmd : command) {
			dockerfile += cmd;
			dockerfile += " ";
		}
		dockerfile = dockerfile.substring(0, dockerfile.length() - 1);
		dockerfile += "\n";
		
		File df = new File(tmp, "Dockerfile");
		if(!df.exists()) df.createNewFile();
		try (FileOutputStream fos = new FileOutputStream(df)) {
			fos.write(dockerfile.getBytes(StandardCharsets.UTF_8));
			fos.flush();
		}
		
		ProcessBuilder builder = new ProcessBuilder("/usr/bin/sudo", "/usr/bin/docker", "build", "-t", image, ".");
		builder.inheritIO();
		builder.directory(tmp);
		Process p = builder.start();
		int exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
		
		
		System.out.println("Creating Container...");
		
		builder = new ProcessBuilder("/usr/bin/sudo", "/usr/bin/docker", "create", "--name", docker_uuid, image);
		builder.inheritIO();
		p = builder.start();
		exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
		
		
		// sudo docker cp test3:/var/cache/apt/archives apt_cache/
		
		System.out.println("Copying Packages...");
		
		File apt_cache = new File(tmp, "apt_cache");
		if(!apt_cache.exists()) apt_cache.mkdirs();
		
		builder = new ProcessBuilder("/usr/bin/sudo", "/usr/bin/docker", "cp", docker_uuid + ":/var/cache/apt/archives", apt_cache.getAbsolutePath());
		builder.inheritIO();
		p = builder.start();
		exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
		
		apt_cache = new File(apt_cache, "archives");
		
		for(File f : apt_cache.listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".deb");
			}
		})) {
			map.put(f, null);
		}
		
		System.out.println("Removing Container...");
		
		builder = new ProcessBuilder("/usr/bin/sudo", "/usr/bin/docker", "rm", docker_uuid);
		builder.inheritIO();
		p = builder.start();
		exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
		
		System.out.println("Start Installation to Repository...");
		
		for(Entry<File, AdditionalObj> e : map.entrySet()) {
			System.out.println("Installing to Repository \"" + e.getKey().getAbsolutePath() + "\" ...");
			
			installDeb(e.getKey(), e.getValue());
			
			System.out.println("Finished current.");
			System.out.println();
		}
		
		
		/*builder = new ProcessBuilder("rm", "-rf", tmp.getAbsolutePath());
		builder.inheritIO();
		p = builder.start();
		exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
		if(tmp.exists()) throw new RuntimeException("Temp. Folder does exists.");*/
		
	}

	private static void installDeb(File target, AdditionalObj additional) throws IOException, InterruptedException {
		ProcessBuilder builder;
		
		if(additional == null) {
			builder = new ProcessBuilder("reprepro", "-C", "main", "includedeb", System.getProperty("code-name"), target.getAbsolutePath());
		} else {
			builder = new ProcessBuilder("reprepro", "-C", "main", "-S", additional.section, "-P", additional.priority, "includedeb", System.getProperty("code-name"), target.getAbsolutePath());
		}
		
		builder.directory(new File("tiger-os"));
		builder.inheritIO();
		Process p = builder.start();
		int exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
	}
	
}

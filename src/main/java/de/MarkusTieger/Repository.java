package de.MarkusTieger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

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
		command.add("nala");
		command.add("install");
		command.add("-y");
		command.add("--download-only");
		
		for(String pack : obj.packages) command.add(pack);
		
		System.out.println("Building Docker-Image...");
		
		String dockerfile = """
				
				FROM ubuntu:latest

				LABEL maintainer="markustieger@gmail.com"

				RUN apt update && apt upgrade -y && apt install -y software-properties-common nala && rm -rf /var/lib/apt/lists/*
				
				
				
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
		
		dockerfile += "RUN nala update\n";
		
		
		/*System.out.println("Installing Packages...");
		
		dockerfile += "RUN ";
		for(String cmd : command) {
			if(cmd.equalsIgnoreCase("--download-only")) continue;
			dockerfile += cmd;
			dockerfile += " ";
		}
		dockerfile = dockerfile.substring(0, dockerfile.length() - 1);
		dockerfile += "\n";
		
		
		System.out.println("Removing Packages...");
		
		dockerfile += "RUN ";
		for(String cmd : command) {
			if(cmd.equalsIgnoreCase("--download-only")) continue;
			if(cmd.equalsIgnoreCase("install")) cmd = "remove";
			dockerfile += cmd;
			dockerfile += " ";
		}
		dockerfile = dockerfile.substring(0, dockerfile.length() - 1);
		dockerfile += "\n";
		
		dockerfile += "RUN rm -rf /var/cache/apt/archives\n"
				   + "RUN mkdir /var/cache/apt/archives\n";*/
		
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
		
		System.out.println("Indexing Packages...");
		
		System.out.println("Reading Local-Repository...");
		
		File list = new File("tiger-os/dists/lorax/main/binary-amd64/Packages");
		List<Properties> l = new ArrayList<>();
		try (Scanner x = new Scanner(list)) {
			Properties prop = null;
			while(x.hasNextLine()) {
				String line = x.nextLine();
				if(line.startsWith(" ")) continue;
				if(line.isEmpty()) {
					if(prop != null) l.add(prop);
					prop = null;
					continue;
				}
				if(prop == null) {
					prop = new Properties();
				}
				String[] arg = line.split(":", 2);
				if(arg.length != 2) {
					System.out.println("Ignoring Line: " + line + " Args: " + arg.length + " ( " + Arrays.toString(arg) + " )");
					continue;
				}
				if(arg[1].startsWith(" ")) arg[1] = arg[1].substring(1);
				prop.setProperty(arg[0], arg[1]);
			}
			if(prop != null) l.add(prop);
		}
		
		System.out.println("Finding Known Repositories...");
		
		List<String> knownRepos = new ArrayList<>();
		
		for(String arch : List.of("amd64", "i386")) {
			
			knownRepos.add("http://packages.linuxmint.com/dists/vanessa/main/binary-" + arch + "/Packages");
			
			for(String ubuntu_repo : List.of("http://archive.ubuntu.com/ubuntu/", "http://security.ubuntu.com/ubuntu/")) {
				for(String distro : List.of("jammy", "jammy-updates", "jammy-backports")) {
					for(String component : List.of("main", "restricted", "universe", "multiverse")) {
						String url = ubuntu_repo + "dists/" + distro + "/" + component + "/binary-" + arch + "/Packages.gz";
						knownRepos.add(url);
					}
				}
			}
		}
		
		List<String> already_exists = new ArrayList<>();
		List<String> inRepo = new ArrayList<>();
		
		System.out.println("Indexing Local-Repository...");
		
		for(Properties prop : l) {
			if(prop.getProperty("Package") == null) continue;
			inRepo.add(prop.getProperty("Package"));
		}
		l.clear();
		
		System.out.println("Downloading Known-Repositories...");
		
		List<File> packageFiles = new ArrayList<>();
		
		File packagesListDir = new File(tmp, "packages");
		if(!packagesListDir.exists()) packagesListDir.mkdirs();
		
		int len;
		byte[] buffer = new byte[1024];
		
		for(String repo : knownRepos) {
			
			System.out.println("Repository: " + repo);
			
			boolean gz = repo.endsWith(".gz");
			URL url = new URL(repo);
			
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestProperty("User-Agent", "Tiger-OS Repository Synchronizer");
			InputStream in = con.getInputStream();
			File f = new File(packagesListDir, UUID.randomUUID() + (gz ? ".gz" : ""));
			if(!f.exists()) f.createNewFile();
			
			try (FileOutputStream fos = new FileOutputStream(f)) {
				while((len = in.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
					fos.flush();
				}
			}
			
			packageFiles.add(f);
			
		}
		
		System.out.println("Reading Known-Repositories...");
		
		int pos = 0;
		for(File f : packageFiles) {
			pos++;
			
			System.out.println(pos + " / " + knownRepos.size());
			
			boolean gz = f.getName().toLowerCase().endsWith(".gz");
			
			InputStream in = new FileInputStream(f);
			if(gz) {
				in = new GZIPInputStream(in);
			}
			try (Scanner x = new Scanner(in)) {
				Properties prop = null;
				while(x.hasNextLine()) {
					String line = x.nextLine();
					if(line.startsWith(" ")) continue;
					if(line.isEmpty()) {
						if(prop != null) l.add(prop);
						prop = null;
						continue;
					}
					if(prop == null) {
						prop = new Properties();
					}
					String[] arg = line.split(":", 2);
					if(arg.length != 2) {
						System.out.println("Ignoring Line: " + line + " Args: " + arg.length + " ( " + Arrays.toString(arg) + " )");
						continue;
					}
					if(arg[1].startsWith(" ")) arg[1] = arg[1].substring(1);
					prop.setProperty(arg[0], arg[1]);
				}
				if(prop != null) l.add(prop);
			}
		}
		
		System.out.println("Indexing Known-Repositories...");
		
		for(Properties prop : l) {
			if(prop.getProperty("Package") == null) continue;
			already_exists.add(prop.getProperty("Package"));
		}
		
		System.out.println("Finding Duplicates Packages...");
		
		List<String> remove = new ArrayList<>();
		
		for(String pack : inRepo) {
			if(already_exists.contains(pack)) {
				System.out.println("Removing: " + pack);
				remove.add(pack);
			}
		}
		
		System.out.println();
		System.out.println("Removing Packages...");
		System.out.println();
		
		for(String rm : remove) {
			
			System.out.println("Removing: " + rm);
			
			builder = new ProcessBuilder("reprepro", "-C", "main", "remove", System.getProperty("code-name"), rm);
			builder.directory(new File("tiger-os"));
			builder.inheritIO();
			p = builder.start();
			exit = p.waitFor();
			if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
			
			System.out.println("Finish current.");
			System.out.println();
			
		}
		
		
		
		/*builder = new ProcessBuilder("rm", "-rf", tmp.getAbsolutePath());
		builder.inheritIO();
		p = builder.start();
		exit = p.waitFor();
		if(exit != 0) throw new RuntimeException("Exit Code is not zero: " + exit);
		if(tmp.exists()) throw new RuntimeException("Temp. Folder does exists.");*/
		
		System.out.println("Finish.");
		
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

package de.MarkusTieger.obj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackagesObj {

	public Map<String, AdditionalObj> direct = new HashMap<>();
	public List<GithubIdentifierObj> github = new ArrayList<>();
	public List<String> repositories = new ArrayList<>();
	public List<String> packages = new ArrayList<>();
	public Map<String, AdditionalObj> override = new HashMap<>();
	public List<KeyServerObj> keys_keyserver = new ArrayList<>();
	
}

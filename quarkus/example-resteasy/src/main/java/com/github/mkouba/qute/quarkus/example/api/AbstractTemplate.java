package com.github.mkouba.qute.quarkus.example.api;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Variant;

import org.jboss.resteasy.core.ResteasyContext;

public abstract class AbstractTemplate {
	
    public class TemplateVariants {

		public final Map<Variant, String> variants;
		public final String defaultTemplate;

		public TemplateVariants(String defaultTemplate, Map<Variant, String> variants) {
			this.defaultTemplate = defaultTemplate;
			this.variants = variants;
		}

		public String getVariantTemplate(MediaType mediaType) {
			return getVariantTemplate(mediaType, defaultTemplate);
		}

		public String getVariantTemplate(MediaType mediaType, String templateIfVariantNotFound) {
			Variant variant = new Variant(mediaType, (String)null, null);
			if(variants.containsKey(variant))
				return variants.get(variant);
			return templateIfVariantNotFound;
		}
	}

//	protected final Map<String, Object> variables;
	protected final Object root;
	protected final String name;

//	public AbstractTemplate(String name, Map<String, Object> variables){
//		this.name = name;
//		this.variables = variables;
//	}

	public AbstractTemplate(String name, Object root){
	    this.name = name;
	    this.root = root;
	}

	public AbstractTemplate(String name){
		this(name, null);
	}

	public AbstractTemplate(Object root){
	    this(getActionName(), root);
	}

	public AbstractTemplate(){
		this(getActionName());
	}

	private static String getActionName() {
		ResourceInfo resourceMethod = ResteasyContext.getContextData(ResourceInfo.class);
		return "templates/"+resourceMethod.getResourceClass().getSimpleName()+"/"+resourceMethod.getResourceMethod().getName();
	}

//	public Map<String, Object> getVariables() {
//		return variables;
//	}

	public Object getRoot() {
	    return root;
	}

	public String getName() {
		return name;
	}
	
//	public AbstractTemplate set(String name, Object value){
//		variables.put(name, value);
//		return this;
//	}
	
	protected TemplateVariants loadVariants() {
		String path = name;
		int lastSlash = path.lastIndexOf('/');
		String templateDir;
		String namePart; 
		if(lastSlash != -1) {
			templateDir = path.substring(0, lastSlash);
			namePart = path.substring(lastSlash+1);
		} else {
			templateDir = ""; // current dir
			namePart = path;
		}
        Map<Variant, String> variants = new HashMap<>();
        try {
            // FIXME: get this damn variants list at compile-time
            URL templateRoot = Thread.currentThread().getContextClassLoader().getResource("META-INF/resources/"+templateDir);
            Path templateRootPath = Paths.get(templateRoot.toURI());
            Files.walk(templateRootPath, 1)
            .map(path2 -> templateRootPath.relativize(path2).toString())
            .filter(entry -> !entry.equals(namePart) && entry.startsWith(namePart))
            .forEach(entry -> {
                String extensionWithDot = entry.substring(namePart.length());
                // .html / .txt
                if(!extensionWithDot.startsWith("."))
                    return;
                String mediaExtension = extensionWithDot.substring(1);
                MediaType mediaType = AbstractTemplate.parseMediaType(mediaExtension);
                variants.put(new Variant(mediaType, (String)null, null), templateDir+"/"+entry);
            });
        } catch (IOException | URISyntaxException e) {
            throw new WebApplicationException(e);
        }
        return new TemplateVariants(templateDir+"/"+namePart, variants);
	}

	public static MediaType parseMediaType(String extension) {
		// FIXME: bigger list, and override in config
		if(extension.equalsIgnoreCase("html"))
			return MediaType.TEXT_HTML_TYPE;
		if(extension.equalsIgnoreCase("xml"))
			return MediaType.APPLICATION_XML_TYPE;
		if(extension.equalsIgnoreCase("txt"))
			return MediaType.TEXT_PLAIN_TYPE;
		if(extension.equalsIgnoreCase("json"))
			return MediaType.APPLICATION_JSON_TYPE;
		System.err.println("Unknown extension type: "+extension);
		return MediaType.APPLICATION_OCTET_STREAM_TYPE;
	}

	public static MediaType parseMediaTypeForTemplate(String templatePath) {
		int lastSlash = templatePath.lastIndexOf('/');
		String templateName;
		if(lastSlash != -1)
			templateName = templatePath.substring(lastSlash+1);
		else
			templateName = templatePath;
		int lastDot = templateName.lastIndexOf('.');
		if(lastDot != -1)
			return parseMediaType(templateName.substring(lastDot+1));
		// no extension
		return MediaType.APPLICATION_OCTET_STREAM_TYPE;
	}
}

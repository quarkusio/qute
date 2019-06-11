package com.github.mkouba.qute.quarkus.example.api;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CompletionStage;

import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Variant;

import com.github.mkouba.qute.Engine;

public class TemplateInstance extends AbstractTemplate {

//	public TemplateInstance(String name, Map<String, Object> variables){
//		super(name, variables);
//	}

	public TemplateInstance(String name, Object root){
	    super(name, root);
	}

	public TemplateInstance(Object root){
	    super(root);
	}

	public TemplateInstance(String name){
		super(name);
	}

	public TemplateInstance(){
		super();
	}

//	@Override
//	public Template set(String name, Object value) {
//		super.set(name, value);
//		return this;
//	}
	
	public CompletionStage<Response> render(Request request) {
		String variant = selectVariant(request);
		Engine engine = CDI.current().select(Engine.class).get();
		StringBuilder buf = new StringBuilder();
		return engine.getTemplate(variant).render(root, part -> buf.append(part))
		    .thenApply(v -> Response.ok(buf.toString(), parseMediaTypeForTemplate(variant)).build());
	}

	public String selectVariant(Request request){
		TemplateVariants variants = loadVariants();
		// no variant
		if(variants.variants.isEmpty())
		    return variants.defaultTemplate;
		Variant selectedVariant = request.selectVariant(new ArrayList<>(variants.variants.keySet()));
		// no acceptable variant
		if(selectedVariant == null) {
		    // if it does not exist, that's special
		    String template = variants.defaultTemplate;
		    if(Files.exists(Paths.get(template)))
		        return template;
		    throw new WebApplicationException(Status.NOT_ACCEPTABLE);
		}
		return variants.variants.get(selectedVariant);
	}
	
}
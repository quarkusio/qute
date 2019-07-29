package com.github.mkouba.qute.quarkus.example.api;

import java.util.concurrent.CompletionStage;

import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.core.MediaType;

import com.github.mkouba.qute.Engine;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.ReactiveMailer;

public class MailInstance extends AbstractTemplate {

	String from;
	String[] to;
	String[] cc;
	String[] bcc;
	String subject;

	public MailInstance(String name, Object root) {
		super(name, root);
	}

	public MailInstance(Object root) {
	    super(root);
	}

	public MailInstance(String name) {
		super(name);
	}
	
//	@Override
//	public Mail set(String name, Object value) {
//		super.set(name, value);
//		return this;
//	}

	public MailInstance from(String address) {
		from = address;
		return this;
	}

	public MailInstance to(String... addresses) {
		to = addresses;
		return this;
	}

	public MailInstance cc(String... addresses) {
		cc = addresses;
		return this;
	}
	
	public MailInstance bcc(String... addresses) {
		bcc = addresses;
		return this;
	}

	public MailInstance subject(String subject) {
		this.subject = subject;
		return this;
	}

	public CompletionStage<Void> send() {
		if(to == null && cc == null && bcc == null)
			throw new IllegalStateException("Missing to, cc or bcc");
		if(subject == null)
			throw new IllegalStateException("Missing subject");
		
		ReactiveMailer mailer = CDI.current().select(ReactiveMailer.class).get();
        Engine engine = CDI.current().select(Engine.class).get();
        Mail mail = new Mail();
        mail.addTo(to);
        mail.setSubject(subject);
        mail.setFrom(from);
        mail.addCc(cc);
        mail.addCc(bcc);
        return renderText(engine)
            .thenCompose(txt -> {
                mail.setText(txt);
                return renderHtml(engine);
            }).thenCompose(html -> {
                mail.setHtml(html);
                return mailer.send(mail);
            });
	}

	public CompletionStage<String> renderText(Engine engine) {
		// FIXME: cache variants?
		TemplateVariants variants = loadVariants();
		System.err.println("Got variants for txt");
		String template = variants.getVariantTemplate(MediaType.TEXT_PLAIN_TYPE);
		StringBuilder buf = new StringBuilder();
		return engine.getTemplate(template).render().setData(root).consume(part -> buf.append(part))
		    .thenApply(v -> buf.toString());
	}

	public CompletionStage<String> renderHtml(Engine engine) {
        // FIXME: cache variants?
        TemplateVariants variants = loadVariants();
        System.err.println("Got variants for html");
        String template = variants.getVariantTemplate(MediaType.TEXT_HTML_TYPE);
        StringBuilder buf = new StringBuilder();
        return engine.getTemplate(template).render().setData(root).consume(part -> buf.append(part))
            .thenApply(v -> buf.toString());
	}
}

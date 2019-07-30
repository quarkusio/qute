package com.github.mkouba.qute.quarkus.resteasy;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.core.interception.jaxrs.SuspendableContainerResponseContext;

import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.Template.Rendering;

@Provider
public class TemplateResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        Object entity = responseContext.getEntity();
        if (entity instanceof Template.Rendering) {
            SuspendableContainerResponseContext ctx = (SuspendableContainerResponseContext) responseContext;
            ctx.suspend();

            MediaType mediaType;
            Template.Rendering rendering = (Rendering) entity;

            if (entity instanceof VariantRendering) {
                VariantRendering variantRendering = (VariantRendering) entity;
                variantRendering.selectVariant(requestContext.getRequest());
                mediaType = TemplateVariantProducer.parseMediaType(variantRendering.getBaseName(),
                        variantRendering.getSelectedVariant());
            } else {
                // TODO how to get media type from non-variant templates?
                mediaType = null;
            }

            try {
                StringBuilder buffer = new StringBuilder();
                rendering.consume(chunk -> buffer.append(chunk))
                        .whenComplete((r, t) -> {
                            if (t == null) {
                                Response resp = Response.ok(buffer.toString(), mediaType).build();
                                // make sure we avoid setting a null media type because that causes
                                // an NPE further down
                                if (resp.getMediaType() != null) {
                                    ctx.setEntity(resp.getEntity(), null, resp.getMediaType());
                                } else {
                                    ctx.setEntity(resp.getEntity());
                                }
                                ctx.setStatus(resp.getStatus());
                                ctx.resume();
                            } else {
                                ctx.resume(t);
                            }
                        });
            } catch (Throwable t) {
                ctx.resume(t);
            }
        }
    }
}

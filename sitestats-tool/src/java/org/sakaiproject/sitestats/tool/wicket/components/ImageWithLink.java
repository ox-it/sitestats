package org.sakaiproject.sitestats.tool.wicket.components;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;


/**
 * @author Nuno Fernandes
 */
public class ImageWithLink extends Panel {
	private static final long		serialVersionUID	= 1L;

	public ImageWithLink(String id) {
		this(id, null, null, null, null);
	}
	
	public ImageWithLink(String id, String imgUrl, String lnkUrl, String lnkLabel, String lnkTarget) {
		super(id);
		setRenderBodyOnly(false);
		add( new ExternalImage("image", imgUrl) );
		ExternalLink lnk = new ExternalLink("link", lnkUrl, lnkLabel);
		if(lnkTarget != null) {
			lnk.add(new AttributeModifier("target", true, new Model(lnkTarget)));
		}
		add(lnk);
	}
}

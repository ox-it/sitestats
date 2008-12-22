package org.sakaiproject.sitestats.tool.wicket.components;

import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.IAjaxIndicatorAware;
import org.apache.wicket.ajax.form.AjaxFormChoiceComponentUpdatingBehavior;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.extensions.ajax.markup.html.WicketAjaxIndicatorAppender;
import org.apache.wicket.markup.html.form.RadioGroup;
import org.apache.wicket.model.IModel;


public abstract class IndicatingAjaxRadioGroup extends RadioGroup implements IAjaxIndicatorAware {
	private static final long						serialVersionUID	= 1L;
	private AbstractAjaxBehavior					ajaxUpdatingBehavior;
	private Object 									forModelObjectOnly;
	private WicketAjaxIndicatorAppender				indicatorAppender  	= new WicketAjaxIndicatorAppender();;

	public IndicatingAjaxRadioGroup(final String id) {
		this(id, null, null, false);
	}

	public IndicatingAjaxRadioGroup(final String id, final Object forModelObjectOnly, final boolean lazyLoadData) {
		this(id, null, forModelObjectOnly, lazyLoadData);
	}
	
	public IndicatingAjaxRadioGroup(final String id, final IModel model, final Object forModelObjectOnly, final boolean lazyLoadData) {
		super(id, model);
		setOutputMarkupId(true);	
		
		if(lazyLoadData) {
			this.forModelObjectOnly = forModelObjectOnly;
			add(indicatorAppender);	
			ajaxUpdatingBehavior = new AjaxFormChoiceComponentUpdatingBehavior() {
				private static final long	serialVersionUID	= 1L;
	
				@Override
				protected void onUpdate(AjaxRequestTarget target) {
					if(forModelObjectOnly != null && forModelObjectOnly.equals(getModelObject())) {
						IndicatingAjaxRadioGroup.this.onUpdate(target);
					}
				}
			};
			add(ajaxUpdatingBehavior);
		}
	}

	public void removeAjaxUpdatingBehavior() {
		if(getBehaviors().contains(ajaxUpdatingBehavior)) {
			remove(ajaxUpdatingBehavior);
		}
	}

	/**
	 * Listener method invoked on an ajax update call
	 * @param target
	 */
	protected abstract void onUpdate(AjaxRequestTarget target);

	public String getAjaxIndicatorMarkupId() {
		if(forModelObjectOnly != null && forModelObjectOnly.equals(getModelObject())) {
			return indicatorAppender.getMarkupId();
		}else{
			return null;
		}
	}

}
package org.sakaiproject.sitestats.tool.jsf;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.CharacterConverter;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.service.legacy.user.User;
import org.sakaiproject.service.legacy.user.cover.UserDirectoryService;

public class UserIdNameConverter extends CharacterConverter {
	
	public String getAsString(FacesContext context, UIComponent component, Object value) {
		String name = null;
		if (value == null) {
			name = "";
		} else {
			if (value instanceof String) {
				try{
					User u = UserDirectoryService.getUser((String)value);					
						name = u.getEid();
						String lName = u.getLastName();
						String fName = u.getFirstName();
						if(lName == null || lName.equals(""))
							name = fName != null? fName : "";
						else if(fName == null || fName.equals(""))
							name = lName != null? lName : "";
						else
							name = lName+", "+fName;
				}catch(IdUnusedException e){
					name = "";
				}
			}
			name = super.getAsString(context, component, (Object)name);
		}

		return name;
	}

}

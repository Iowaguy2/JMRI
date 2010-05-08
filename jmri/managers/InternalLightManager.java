// InternalLightManager.java

package jmri.managers;

import jmri.Light;
import jmri.implementation.AbstractVariableLight;
import jmri.managers.AbstractLightManager;

/**
 * Implement a light manager for "Internal" (virtual) lights.
 *
 * @author			Bob Jacobsen Copyright (C) 2009
 * @version			$Revision: 1.8 $
 */
public class InternalLightManager extends AbstractLightManager {

    /**
     * Create and return an internal (no layout connection) Light
     */
    protected Light createNewLight(String systemName, String userName) {
        return new AbstractVariableLight(systemName, userName){
            //protected void forwardCommandChangeToLayout(int s) {}
            protected void sendIntensity(double intensity) {}
            protected void sendOnOffCommand(int newState) {}
            protected int getNumberOfSteps() { return 100; }
        };
    }
    
    public String getSystemPrefix() { return "I"; }
    public boolean validSystemNameConfig(String systemName) {
        return true;
    }
    public boolean validSystemNameFormat(String systemName) {
        return true;
    }
	public boolean supportsVariableLights(String systemName) {
		return true;
	}
    public boolean allowMultipleAdditions(String systemName) { 
		return true;  }

}

/* @(#)InternalLightManager.java */

<!--- 
 *
 * Copyright (c) 2015, Lucee Assosication Switzerland. All rights reserved.*
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 ---><cfscript>

component extends="org.lucee.cfml.test.LuceeTestCase"	{

	variables.pack="LDEV0270";
	variables.path='./#pack#/';
	variables.names=['BaselineTag','Baseline','Base','BaseTag'];


    public void function testTagConstructor(){
    	local.appendix=touchFiles();
    	try{
	    	savecontent variable="local.c" {
		    	local.cfc=createObject("component","LDEV0270.BaselineTag-#appendix#");
	    	}
		    assertEquals("-static-constructor--body-constructor-",trim(c));
	    }
	    finally {
	    	delete(appendix);
	    }
    }

    public void function testScriptConstructor(){
    	local.appendix=touchFiles();
    	try{
	    	savecontent variable="local.c" {
		    	local.cfc=createObject("component","LDEV0270.Baseline-#appendix#");
	    	}
		    assertEquals("-static-constructor--body-constructor-",trim(c));
	    }
	    finally {
	    	delete(appendix);
	    }
    }


    private function touchFiles() {
    	// we need to create a new component, otherwise testbox already invokes the component
    	local.appendix=createUniqueID();
    	
	    loop array=names item="local.item" {
	    	local.src=(path&item&'.cfc');
	    	local.trg=(path&item&'-'&appendix&'.cfc');
	    	fileCopy(src,trg);
	    }
	    return appendix;
	}

    private function delete(appendix) {
	    loop array=names item="local.item" {
	    	local.trg=(path&item&'-'&appendix&'.cfc');
	    	fileDelete(trg);
	    }
	}

}
</cfscript>
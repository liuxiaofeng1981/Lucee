/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
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
 */
package lucee.runtime;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import lucee.commons.io.IOUtil;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.util.ResourceUtil;
import lucee.commons.lang.ClassUtil;
import lucee.commons.lang.StringUtil;
import lucee.commons.lang.types.RefBoolean;
import lucee.commons.lang.types.RefBooleanImpl;
import lucee.loader.engine.CFMLEngine;
import lucee.runtime.compiler.CFMLCompilerImpl.Result;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigImpl;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.config.ConfigWebImpl;
import lucee.runtime.config.ConfigWebUtil;
import lucee.runtime.config.Constants;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.ExpressionException;
import lucee.runtime.exp.MissingIncludeException;
import lucee.runtime.exp.PageException;
import lucee.runtime.exp.PageRuntimeException;
import lucee.runtime.exp.TemplateException;
import lucee.runtime.functions.system.GetDirectoryFromPath;
import lucee.runtime.op.Caster;
import lucee.runtime.type.util.ArrayUtil;
import lucee.runtime.type.util.ListUtil;

/**
 * represent a cfml file on the runtime system
 */
public final class PageSourceImpl implements PageSource {

	private static final long serialVersionUID = -7661676586215092539L;
	//public static final byte LOAD_NONE=1;
    public static final byte LOAD_ARCHIVE=2;
    public static final byte LOAD_PHYSICAL=3;
    
    //private byte load=LOAD_NONE;

	private final MappingImpl mapping;
    private final String relPath;
    
    private boolean isOutSide;
    
    private String className;
    private String packageName;
    private String javaName;

    private Resource physcalSource;
    private Resource archiveSource;
    private String fileName;
    private String compName;
    private Page page;
	private long lastAccess;	
	private int accessCount=0;
	private boolean flush=false;
    //private boolean recompileAlways;
    //private boolean recompileAfterStartUp;
    
    private PageSourceImpl() {
    	mapping=null;
        relPath=null;
    }
    
    
    /**
	 * constructor of the class
     * @param mapping
     * @param realPath
	 */
	PageSourceImpl(MappingImpl mapping,String realPath) {
		this.mapping=mapping;
        realPath=realPath.replace('\\','/');
        if(realPath.indexOf("//")!=-1) {
        	realPath=StringUtil.replace(realPath, "//", "/",false);
        }
        
        
		if(realPath.indexOf('/')!=0) {
		    if(realPath.startsWith("../")) {
				isOutSide=true;
			}
			else if(realPath.startsWith("./")) {
				realPath=realPath.substring(1);
			}
			else {
				realPath="/"+realPath;
			}
		}
		this.relPath=realPath;
	    
	}
	
	
	
	/**
	 * private constructor of the class
	 * @param mapping
	 * @param realPath
	 * @param isOutSide
	 */
    PageSourceImpl(MappingImpl mapping, String realPath, boolean isOutSide) {
    	//recompileAlways=mapping.getConfig().getCompileType()==Config.RECOMPILE_ALWAYS;
        //recompileAfterStartUp=mapping.getConfig().getCompileType()==Config.RECOMPILE_AFTER_STARTUP || recompileAlways;
        this.mapping=mapping;
	    this.isOutSide=isOutSide;
	    if(realPath.indexOf("//")!=-1) {
        	realPath=StringUtil.replace(realPath, "//", "/",false);
        }this.relPath=realPath;
		
	}
	
	/**
	 * return page when already loaded, otherwise null
	 * @param pc
	 * @param config
	 * @return
	 * @throws PageException
	 */
	public Page getPage() {
		return page;
	}
	
	public PageSource getParent(){
		if(relPath.equals("/")) return null;
		if(StringUtil.endsWith(relPath, '/'))
			return new PageSourceImpl(mapping, GetDirectoryFromPath.invoke(relPath.substring(0, relPath.length()-1)));
		return new PageSourceImpl(mapping, GetDirectoryFromPath.invoke(relPath));
	}
	
	@Override
	public Page loadPage(PageContext pc, boolean forceReload) throws PageException {
		if(forceReload) page=null;
		
		Page page=this.page;
		if(mapping.isPhysicalFirst()) {
			page=loadPhysical(pc,page);
			if(page==null) page=loadArchive(page); 
	        if(page!=null) return page;
	    }
	    else {
	        page=loadArchive(page);
	        if(page==null)page=loadPhysical(pc,page);
	        if(page!=null) return page;
	    }
		throw new MissingIncludeException(this);
	    
	}
	
	@Override
	public Page loadPageThrowTemplateException(PageContext pc, boolean forceReload, Page defaultValue) throws TemplateException {
		if(forceReload) page=null;
		
		Page page=this.page;
		if(mapping.isPhysicalFirst()) {
	        page=loadPhysical(pc,page);
	        if(page==null) page=loadArchive(page); 
	        if(page!=null) return page;
	    }
	    else {
	    	page=loadArchive(page);
	        if(page==null)page=loadPhysical(pc,page);
	        if(page!=null) return page;
	    }
	    return defaultValue;
	}

	
	@Override
	public Page loadPage(PageContext pc, boolean forceReload, Page defaultValue) {
		if(forceReload) page=null;
		
		Page page=this.page;
		if(mapping.isPhysicalFirst()) {
	        try {
				page=loadPhysical(pc,page);
			}
	        catch (TemplateException e) {
				page=null;
			}
	        if(page==null) page=loadArchive(page); 
	        if(page!=null) return page;
	    }
	    else {
	    	page=loadArchive(page);
	        if(page==null){
	        	try {
					page=loadPhysical(pc,page);
				}
	        	catch (TemplateException e) {}
	        }
	        if(page!=null) return page;
	    }
	    return defaultValue;
	}
	
    private Page loadArchive(Page page) {
    	if(!mapping.hasArchive()) return null;
		if(page!=null && page.getLoadType()==LOAD_ARCHIVE) return page;
        try {
            synchronized(this) {
                Class clazz=mapping.getArchiveClass(getClassName());
                
                this.page=page=newInstance(clazz);
                page.setPageSource(this);
                //page.setTimeCreated(System.currentTimeMillis());
                page.setLoadType(LOAD_ARCHIVE);
    			////load=LOAD_ARCHIVE;
    			return page;
            }
        } 
        catch (Exception e) {
        	// MUST print.e(e); is there a better way?
        	return null;
        }
    }
    
    /**
     * throws only an exception when compilation fails
     * @param pc
     * @param page
     * @return
     * @throws PageException
     */
    private Page loadPhysical(PageContext pc,Page page) throws TemplateException {
    	if(!mapping.hasPhysical()) return null;
    	
    	ConfigWeb config=pc.getConfig();
    	PageContextImpl pci=(PageContextImpl) pc;
    	if((mapping.getInspectTemplate()==Config.INSPECT_NEVER || pci.isTrusted(page)) && isLoad(LOAD_PHYSICAL)) return page;
    	Resource srcFile = getPhyscalFile();
    	
		long srcLastModified = srcFile.lastModified();
        if(srcLastModified==0L) return null;
    	
		// Page exists    
			if(page!=null) {
			//if(page!=null && !recompileAlways) {
				if(srcLastModified!=page.getSourceLastModified()) {
					this.page=page=compile(config,mapping.getClassRootDirectory(),page,false,pc.ignoreScopes());
                	page.setPageSource(this);
					page.setLoadType(LOAD_PHYSICAL);
				}
		    	
			}
		// page doesn't exist
			else {
                ///synchronized(this) {
                    Resource classRootDir=mapping.getClassRootDirectory();
                    Resource classFile=classRootDir.getRealResource(getJavaName()+".class");
                    boolean isNew=false;
                    // new class
                    if(flush || !classFile.exists()) {
                    //if(!classFile.exists() || recompileAfterStartUp) {
                    	this.page=page= compile(config,classRootDir,null,false,pc.ignoreScopes());
                    	flush=false;
                        isNew=true;
                    }
                    // load page
                    else {
                    	try {
                    		this.page=page=newInstance(mapping.getPhysicalClass(this.getClassName()));
    					} catch (Throwable t) {t.printStackTrace();
							this.page=page=null;
						}
                    	if(page==null) this.page=page=compile(config,classRootDir,null,false,pc.ignoreScopes());
                              
                    }
                    
                    // check if version changed or lasMod
                    if(!isNew && 
                    		(
                    				srcLastModified!=page.getSourceLastModified()
                    				||
                    				page.getVersion()!=pc.getConfig().getFactory().getEngine().getInfo().getFullVersionInfo()
                    		)
                    ) {
                    	isNew=true;
                    	this.page=page=compile(config,classRootDir,page,false,pc.ignoreScopes());
    				}
                    
                    page.setPageSource(this);
    				page.setLoadType(LOAD_PHYSICAL);

			}
			pci.setPageUsed(page);
			return page;
    }

    public void flush() {
		page=null;
		flush=true;
	}

    private boolean isLoad(byte load) {
		return page!=null && load==page.getLoadType();
	}
    

	private synchronized Page compile(ConfigWeb config,Resource classRootDir, Page existing, boolean returnValue, boolean ignoreScopes) throws TemplateException {
		try {
			return _compile(config, classRootDir,existing,returnValue,ignoreScopes);
        }
			catch(RuntimeException re) {re.printStackTrace();
	    	String msg=StringUtil.emptyIfNull(re.getMessage());
	    	if(StringUtil.indexOfIgnoreCase(msg, "Method code too large!")!=-1) {
	    		throw new TemplateException("There is too much code inside the template ["+getDisplayPath()+"], "+Constants.NAME+" was not able to break it into pieces, move parts of your code to an include or a external component/function",msg);
	    	}
	    	throw re;
	    }
        catch(ClassFormatError e) {
        	String msg=StringUtil.emptyIfNull(e.getMessage());
        	if(StringUtil.indexOfIgnoreCase(msg, "Invalid method Code length")!=-1) {
        		throw new TemplateException("There is too much code inside the template ["+getDisplayPath()+"], "+Constants.NAME+" was not able to break it into pieces, move parts of your code to an include or a external component/function",msg);
        	}
        	throw new TemplateException("ClassFormatError:"+e.getMessage());
        }
        catch(Throwable t) {
        	if(t instanceof TemplateException) throw (TemplateException)t;
        	throw new PageRuntimeException(Caster.toPageException(t));
        	//throw new TemplateException(t.getClass().getName()+":"+t.getMessage());
        }
	}

	private Page _compile(ConfigWeb config,Resource classRootDir, Page existing,boolean returnValue, boolean ignoreScopes) throws IOException, SecurityException, IllegalArgumentException, PageException {
        ConfigWebImpl cwi=(ConfigWebImpl) config;
        int dialect=getDialect();
        
        long now;
        if((getPhyscalFile().lastModified()+10000)>(now=System.currentTimeMillis()))
        	cwi.getCompiler().watch(this,now);//SystemUtil.get
        
                
        Result result = cwi.getCompiler().
        	compile(cwi,this,cwi.getTLDs(dialect),cwi.getFLDs(dialect),classRootDir,returnValue,ignoreScopes);
        
        try{
        	
        	Class<?> clazz = mapping.getPhysicalClass(getClassName(), result.barr);
        	return  newInstance(clazz);
        }
        catch(Throwable t){
        	PageException pe = Caster.toPageException(t);
        	pe.setExtendedInfo("failed to load template "+getDisplayPath());
        	throw pe;
        }
    }

    private Page newInstance(Class clazz) throws SecurityException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    	Constructor<?> c = clazz.getConstructor(new Class[]{PageSource.class});
		return (Page) c.newInstance(new Object[]{this});
	}


	/**
     * return source path as String 
     * @return source path as String
     */
    @Override
	public String getDisplayPath() {
        if(!mapping.hasArchive())  	{
        	return StringUtil.toString(getPhyscalFile(), null);
        }
        else if(isLoad(LOAD_PHYSICAL))	{
        	return StringUtil.toString(getPhyscalFile(), null);
        }
        else if(isLoad(LOAD_ARCHIVE))	{
        	return StringUtil.toString(getArchiveSourcePath(), null);
        }
        else {
            boolean pse = physcalExists();
            boolean ase = archiveExists();
            
            if(mapping.isPhysicalFirst()) {
                if(pse)return getPhyscalFile().toString();
                else if(ase)return getArchiveSourcePath();
                return getPhyscalFile().toString();
            }
            if(ase)return getArchiveSourcePath();
            else if(pse)return getPhyscalFile().toString();
            return getArchiveSourcePath();
        }
    }
    
    public boolean isComponent() {
    	String ext = ResourceUtil.getExtension(getRealpath(), "");
    	if(getDialect()==CFMLEngine.DIALECT_CFML)
    		return Constants.isCFMLComponentExtension(ext);
		return Constants.isLuceeComponentExtension(ext);
    }
    
    /**
	 * return file object, based on physical path and realpath
	 * @return file Object
	 */
	private String getArchiveSourcePath() {
	    return "zip://"+mapping.getArchive().getAbsolutePath()+"!"+relPath; 
	}

    /**
	 * return file object, based on physical path and realpath
	 * @return file Object
	 */
    @Override
	public Resource getPhyscalFile() {
        if(physcalSource==null) {
            if(!mapping.hasPhysical()) {
            	return null;
            }
			physcalSource=ResourceUtil.toExactResource(mapping.getPhysical().getRealResource(relPath));
        }
        return physcalSource;
	}
    
    public Resource getArchiveFile() {
    	if(archiveSource==null) {
	    	if(!mapping.hasArchive()) return null;
	    	String path="zip://"+mapping.getArchive().getAbsolutePath()+"!"+relPath;
	    	archiveSource = ThreadLocalPageContext.getConfig().getResource(path);
    	}
        return archiveSource;
	}
    

    /**
	 * merge to realpath to one
	 * @param mapping 
	 * @param parentRealPath 
	 * @param newRealPath
	 * @param isOutSide 
	 * @return merged realpath
	 */
	private static String mergeRealPathes(Mapping mapping,String parentRealPath, String newRealPath, RefBoolean isOutSide) {
		parentRealPath=pathRemoveLast(parentRealPath,isOutSide);
		while(newRealPath.startsWith("../")) {
			parentRealPath=pathRemoveLast(parentRealPath,isOutSide);
			newRealPath=newRealPath.substring(3);
		}
		
		// check if come back
		String path=parentRealPath.concat("/").concat(newRealPath);
		
		if(path.startsWith("../")) {
			int count=0;
			do {
				count++;
				path=path.substring(3);
			}while(path.startsWith("../"));
			
			String strRoot=mapping.getPhysical().getAbsolutePath().replace('\\','/');
			if(!StringUtil.endsWith(strRoot,'/')) {
				strRoot+='/';
			}
			int rootLen=strRoot.length();
			String[] arr=ListUtil.toStringArray(ListUtil.listToArray(path,'/'),"");//path.split("/");
			int tmpLen;
			for(int i=count;i>0;i--) {
				if(arr.length>i) {
					String tmp='/'+list(arr,0,i);
					tmpLen=rootLen-tmp.length();
					if(strRoot.lastIndexOf(tmp)==tmpLen && tmpLen>=0) {
						StringBuffer rtn=new StringBuffer();
						while(i<count-i) {
							count--;
							rtn.append("../");
						}
						isOutSide.setValue(rtn.length()!=0);
						return (rtn.length()==0?"/":rtn.toString())+list(arr,i,arr.length);
					}
				}
			}
		}
		return parentRealPath.concat("/").concat(newRealPath);
	}

	/**
	 * convert a String array to a string list, but only part of it 
	 * @param arr String Array
	 * @param from start from here
	 * @param len how many element
	 * @return String list
	 */
	private static String list(String[] arr,int from, int len) {
		StringBuffer sb=new StringBuffer();
		for(int i=from;i<len;i++) {
			sb.append(arr[i]);
			if(i+1!=arr.length)sb.append('/');
		}
		return sb.toString();
	}

	
	
	/**
	 * remove the last elemtn of a path
	 * @param path path to remove last element from it
	 * @param isOutSide 
	 * @return path with removed element
	 */
	private static String pathRemoveLast(String path, RefBoolean isOutSide) {
		if(path.length()==0) {
			isOutSide.setValue(true);
			return "..";
		}
		else if(path.endsWith("..")){
		    isOutSide.setValue(true);
			return path.concat("/..");//path+"/..";
		}
		return path.substring(0,path.lastIndexOf('/'));
	}
	
	@Override
	public String getRealpath() {
		return relPath;
	}	
	@Override
	public String getRealpathWithVirtual() {
		if(mapping.getVirtual().length()==1 || mapping.ignoreVirtual())
			return relPath;
		return mapping.getVirtual()+relPath;
	}

	private String _getClassName() {
		if(className==null) createClassAndPackage();
		return className;
	}
	
	@Override
	public String getClassName() {
		if(className==null) createClassAndPackage();
		if(packageName.length()==0) return className;
		return packageName.concat(".").concat(className);
	}

    @Override
    public String getFileName() {
		if(fileName==null) createClassAndPackage();
        return fileName;
    }
	
	public String getJavaName() {
		if(javaName==null) createClassAndPackage();
		return javaName;
	}

	private String _getPackageName() {
		if(packageName==null) createClassAndPackage();
		return packageName;
	}
	@Override
	public String getComponentName() {
		if(compName==null) createComponentName();
		return compName;
	}
	
	
	private synchronized void createClassAndPackage() {
		String str=relPath;
		StringBuilder packageName=new StringBuilder();
		StringBuilder javaName = new StringBuilder();
		String[] arr=ListUtil.toStringArrayEL(ListUtil.listToArrayRemoveEmpty(str,'/'));
		
		String varName;
		for(int i=0;i<arr.length;i++) {
			if(i==(arr.length-1)) {
				int index=arr[i].lastIndexOf('.');
				if(index!=-1){
					String ext=arr[i].substring(index+1);
					varName=StringUtil.toVariableName(arr[i].substring(0,index)+"_"+ext);
				}
				else varName=StringUtil.toVariableName(arr[i]);
				varName=varName+(getDialect()==CFMLEngine.DIALECT_CFML?Constants.CFML_CLASS_SUFFIX:Constants.LUCEE_CLASS_SUFFIX);
				className=varName.toLowerCase();
				fileName=arr[i];
			}
			else {
				varName=StringUtil.toVariableName(arr[i]);
				if(i!=0) {
					packageName.append('.');
				}
				packageName.append(varName);
			}
			javaName.append('/');
			javaName.append(varName);
		}
		this.packageName=packageName.toString().toLowerCase();
		this.javaName=javaName.toString().toLowerCase();
	}
	
	

	private synchronized void createComponentName() {
		Resource res = this.getPhyscalFile();
	    String str=null;
		if(res!=null) {
			
			str=res.getAbsolutePath();
			int begin=str.length()-relPath.length();
			if(begin<0) { // TODO patch, analyze the complete functinality and improve
				str=ListUtil.last(str, "\\/");
			}
			else {
				str=str.substring(begin);
				if(!str.equalsIgnoreCase(relPath)) {
					str=relPath;
				}
			}
		}
		else str=relPath;
	    
		StringBuffer compName=new StringBuffer();
		String[] arr;
		
		// virtual part
		if(!mapping.ignoreVirtual()) {
			arr=ListUtil.toStringArrayEL(ListUtil.listToArrayRemoveEmpty(mapping.getVirtual(),"\\/"));
			for(int i=0;i<arr.length;i++) {
				if(compName.length()>0) compName.append('.');
				compName.append(arr[i]);
			}
		}
		
		// physical part
		arr=ListUtil.toStringArrayEL(ListUtil.listToArrayRemoveEmpty(str,'/'));	
		for(int i=0;i<arr.length;i++) {
		    if(compName.length()>0) compName.append('.');
			if(i==(arr.length-1)) {
			    compName.append(ResourceUtil.removeExtension(arr[i], arr[i]));
			}
			else compName.append(arr[i]);
		}
		this.compName=compName.toString();
	}

    @Override
    public Mapping getMapping() {
        return mapping;
    }

    @Override
    public boolean exists() {
    	if(mapping.isPhysicalFirst())
	        return physcalExists() || archiveExists();
	    return archiveExists() || physcalExists();
    }

    @Override
    public boolean physcalExists() {
        return ResourceUtil.exists(getPhyscalFile());
    }
    
    private boolean archiveExists() {
        if(!mapping.hasArchive())return false;
        try {
        	String clazz = getClassName();
        	if(clazz==null) return getArchiveFile().exists();
        	mapping.getArchiveClass(clazz);
        	return true;
        } 
        catch(ClassNotFoundException cnfe){
        	return false;
        }
        catch (Exception e) {
            return getArchiveFile().exists();
        }
    }

    /**
     * return the inputstream of the source file
     * @return return the inputstream for the source from ohysical or archive
     * @throws FileNotFoundException
     */
    private InputStream getSourceAsInputStream() throws IOException {
        if(!mapping.hasArchive()) 		return IOUtil.toBufferedInputStream(getPhyscalFile().getInputStream());
        else if(isLoad(LOAD_PHYSICAL))	return IOUtil.toBufferedInputStream(getPhyscalFile().getInputStream());
        else if(isLoad(LOAD_ARCHIVE)) 	{
            StringBuffer name=new StringBuffer(_getPackageName().replace('.','/'));
            if(name.length()>0)name.append("/");
            name.append(getFileName());
            
            return mapping.getArchiveResourceAsStream(name.toString());
        }
        else {
            return null;
        }
    }
    @Override
    public String[] getSource() throws IOException {
        //if(source!=null) return source;
        InputStream is = getSourceAsInputStream();
        if(is==null) return null;
        try {
        	return IOUtil.toStringArray(IOUtil.getReader(is,getMapping().getConfig().getTemplateCharset()));
        }
        finally {
        	IOUtil.closeEL(is);
        }
    }

    @Override
    public boolean equals(Object obj) {
    	if(this==obj) return true;  
    	if(obj instanceof PageSourceImpl)
    		return _getClassName().equals(((PageSourceImpl)obj)._getClassName());
    	if(obj instanceof PageSource) 
    		return _getClassName().equals(ClassUtil.extractName(((PageSource)obj).getClassName()));
    	return false;
    	
    }
    
    /**
     * is given object equal to this
     * @param other
     * @return is same
     */
    public boolean equals(PageSource ps) {
        if(this==ps) return true;
        
        if(ps instanceof PageSourceImpl)
    		return _getClassName().equals(((PageSourceImpl)ps)._getClassName());
    	return _getClassName().equals(ClassUtil.extractName(ps.getClassName()));
        
        
    }

	@Override
	public PageSource getRealPage(String realPath) {
	    if(realPath.equals(".") || realPath.equals(".."))realPath+='/';
	    else realPath=realPath.replace('\\','/');
	    RefBoolean _isOutSide=new RefBooleanImpl(isOutSide);
	    
	    
		if(realPath.indexOf('/')==0) {
		    _isOutSide.setValue(false);
		}
		else if(realPath.startsWith("./")) {
			realPath=mergeRealPathes(mapping,this.relPath, realPath.substring(2),_isOutSide);
		}
		else {
			realPath=mergeRealPathes(mapping,this.relPath, realPath,_isOutSide);
		}
		return mapping.getPageSource(realPath,_isOutSide.toBooleanValue());
	}
	
	@Override
	public final void setLastAccessTime(long lastAccess) {
		this.lastAccess=lastAccess;
	}	
	
	@Override
	public final long getLastAccessTime() {
		return lastAccess;
	}

	@Override
	public synchronized final void setLastAccessTime() {
		accessCount++;
		this.lastAccess=System.currentTimeMillis();
	}	
	
	@Override
	public final int getAccessCount() {
		return accessCount;
	}

    @Override
    public Resource getResource() {
    	Resource p = getPhyscalFile();
    	Resource a = getArchiveFile();
    	if(mapping.isPhysicalFirst()){
    		if(a==null) return p;
        	if(p==null) return a;
        	
    		if(p.exists()) return p;
    		if(a.exists()) return a;
    		return p;
    	}
    	if(p==null) return a;
    	if(a==null) return p;
    	
    	if(a.exists()) return a;
    	if(p.exists()) return p;
    	return a;
    	
    	//return getArchiveFile();
    }
    
    @Override
    public Resource getResourceTranslated(PageContext pc) throws ExpressionException {
    	Resource res = null;
    	if(!isLoad(LOAD_ARCHIVE)) res=getPhyscalFile();
    	
    	// there is no physical resource
		if(res==null){
        	String path=getDisplayPath();
        	if(path!=null){
        		if(path.startsWith("ra://"))
        			path="zip://"+path.substring(5);
        		res=ResourceUtil.toResourceNotExisting(pc, path,false,false);
        	}
        }
		return res;
    }


    public void clear() {
    	if(page!=null){
    		page=null;
    	}
    }
    
    /**
     * clear page, but only when page use the same clasloader as provided
     * @param cl
     */
    public void clear(ClassLoader cl) {
    	if(page!=null && page.getClass().getClassLoader().equals(cl)){
    		page=null;
    	}
    }

	public boolean isLoad() {
		return page!=null;////load!=LOAD_NONE;
	}

	@Override
	public String toString() {
		return getDisplayPath();
	}

	public static PageSource best(PageSource[] arr) {
		if(ArrayUtil.isEmpty(arr)) return null;
		if(arr.length==1)return arr[0];
		for(int i=0;i<arr.length;i++) {
			if(pageExist(arr[i])) return arr[i];
		}
		return arr[0];
	}

	public static boolean pageExist(PageSource ps) {
		return (ps.getMapping().isTrusted() && ((PageSourceImpl)ps).isLoad()) || ps.exists();
	}

	public static Page loadPage(PageContext pc,PageSource[] arr,Page defaultValue) throws PageException {
		if(ArrayUtil.isEmpty(arr)) return null;
		Page p;
		for(int i=0;i<arr.length;i++) {
			p=arr[i].loadPageThrowTemplateException(pc,false,(Page)null);
			if(p!=null) return p;
		}
		return defaultValue;
	}

	public static Page loadPage(PageContext pc,PageSource[] arr) throws PageException {
		if(ArrayUtil.isEmpty(arr)) return null;
		
		Page p;
		for(int i=0;i<arr.length;i++) {
			p=arr[i].loadPageThrowTemplateException(pc,false,(Page)null);
			if(p!=null) return p;
		}
		throw new MissingIncludeException(arr[0]);
	}


	@Override
	public int getDialect() {
		Config c = getMapping().getConfig();
		if(!((ConfigImpl)c).allowLuceeDialect()) return CFMLEngine.DIALECT_CFML;
		// MUST improve performance on this
		ConfigWeb cw=null;
		
		String ext=ResourceUtil.getExtension(relPath, Constants.getCFMLComponentExtension());
		
		if(c instanceof ConfigWeb)
			cw=(ConfigWeb) c;
		else {
			c=ThreadLocalPageContext.getConfig();
			if(c instanceof ConfigWeb)
				cw=(ConfigWeb) c;
		}
		if(cw!=null) {
			return ((CFMLFactoryImpl)cw.getFactory())
					.toDialect(ext,CFMLEngine.DIALECT_CFML);
		}
		
		return ConfigWebUtil.toDialect(ext, CFMLEngine.DIALECT_CFML);
	}

	/**
	 * return if the PageSource represent a template (no component,no interface)
	 * @param pc
	 * @param ps
	 * @return
	 * @throws PageException
	 */
	public static boolean isTemplate(PageContext pc,PageSource ps, boolean defaultValue) {
		try {
			return !(ps.loadPage(pc, false) instanceof CIPage);
		} catch (PageException e) {e.printStackTrace();
			return defaultValue;
		}
	}


	@Override
	public boolean executable() {
		return (getMapping().getInspectTemplate()==Config.INSPECT_NEVER && isLoad()) || exists();
	}
}
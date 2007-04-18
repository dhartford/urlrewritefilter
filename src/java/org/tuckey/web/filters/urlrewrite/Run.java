/**
 * Copyright (c) 2005, Paul Tuckey
 * All rights reserved.
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */
package org.tuckey.web.filters.urlrewrite;

import org.tuckey.web.filters.urlrewrite.extend.RewriteMatch;
import org.tuckey.web.filters.urlrewrite.utils.Log;
import org.tuckey.web.filters.urlrewrite.utils.StringMatchingMatcher;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Hashtable;


/**
 * Defines a run element, the ability to run a methodStr
 * (eg, xx(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse))
 *
 * @author Paul Tuckey
 * @version $Revision: 43 $ $Date: 2006-10-31 17:29:59 +1300 (Tue, 31 Oct 2006) $
 */
public class Run {

    private static Log log = Log.getLog(Run.class);

    /**
     * Weather or not the user wants the classStr created for each run.
     */
    private boolean newEachTime = false;

    private String classStr;

    private static final String DEAULT_METHOD_STR = "run";
    private String methodStr = DEAULT_METHOD_STR;

    /**
     * Used to identify the condition.
     */
    private int id = 0;

    private String error = null;

    private boolean valid = false;
    private boolean initialised = false;

    /**
     * The instance of the classStr to run.  Note, will be null if newEachTime is true.
     */
    private Object runClassInstance;

    /**
     * handles to the methods we are going to run.
     */
    private Constructor runConstructor;
    private Method initMethod;
    private Method filterInitMethod;
    private Method runMethod;
    private Class[] runMethodParams;
    private String[] runMethodParamNames;
    private boolean runMethodUseDefaultParams = true;
    private Method destroyMethod;

    /**
     * The config that we pass to the objectwe are trying to run.
     */
    private RunConfig runServletConfig;

    private Hashtable initParams = new Hashtable();

    private static boolean loadClass = true;

    private static Class[][] runMethodPossibleSignatures = {
            {ServletRequest.class, ServletResponse.class},
            {HttpServletRequest.class, HttpServletResponse.class}
    };
    private boolean filter = false;


    /**
     * @see #initialise(ServletContext, Class) initialise
     */
    public boolean initialise(ServletContext context) {
        return initialise(context, null);
    }

    /**
     * Initialise the Run, this will check specified classStr, constructor and methodStr exist.
     */
    public boolean initialise(ServletContext context, Class extraParam) {
        log.debug("initialising run");
        runServletConfig = new RunConfig(context, initParams);
        initialised = true;
        valid = false;
        if (StringUtils.isBlank(classStr)) {
            setError("cannot initialise run " + id + " value is empty");
            return valid;
        }
        if (methodStr == null) {
            methodStr = DEAULT_METHOD_STR;
        }
        log.debug("methodStr: " + methodStr);

        String rawMethodStr = methodStr;
        int bkStart = rawMethodStr.indexOf('(');
        int bkEnd = rawMethodStr.indexOf(')');
        if (bkStart != -1 && bkEnd != -1 && (bkEnd - bkStart) > 0) {
            runMethodUseDefaultParams = false;
            // set method str back to just method name
            methodStr = rawMethodStr.substring(0, bkStart);
            // get the list of params ie, "String name, int id"
            String paramsList = rawMethodStr.substring(bkStart + 1, bkEnd);
            paramsList = StringUtils.trimToNull(paramsList);
            if (paramsList != null) {
                String[] params = paramsList.split(",");
                // validate and clean the incoming params list
                Class[] paramClasses = new Class[params.length];
                String[] paramNames = new String[params.length];
                for (int i = 0; i < params.length; i++) {
                    String param = StringUtils.trimToNull(params[i]);
                    if (param == null) continue;
                    if (param.contains(" ")) {
                        String paramName = StringUtils.trimToNull(param.substring(param.indexOf(" ")));
                        if (paramName != null) {
                            log.debug("param name: " + paramName);
                            paramNames[i] = paramName;
                        }
                        param = param.substring(0, param.indexOf(' '));
                    }
                    Class clazz = parseClass(param);
                    if (clazz == null) return valid;
                    paramClasses[i] = clazz;
                }
                runMethodParams = paramClasses;
                runMethodParamNames = paramNames;
            }
        }
        if (loadClass) {
            prepareRunObject(extraParam);
        } else {
            valid = true;
        }
        return valid;
    }

    /**
     * Turns a class or language keyword name into the Class object, works with short names, ie, L - Long.
     * <p/>
     * boolean Z
     * byte B
     * char C
     * class or interface Lclassname;
     * double D
     * float F
     * int I
     * long J
     * short S
     *
     * @see Class
     */
    private Class parseClass(String param) {
        // do shortcuts
        Class paramClass = null;
        if ("boolean".equals(param) || "bool".equals(param) || "z".equalsIgnoreCase(param)) paramClass = boolean.class;
        if ("byte".equals(param) || "b".equalsIgnoreCase(param)) paramClass = byte.class;
        if ("char".equals(param) || "c".equalsIgnoreCase(param)) paramClass = char.class;
        if ("short".equals(param) || "s".equalsIgnoreCase(param)) paramClass = short.class;
        if ("int".equals(param) || "i".equalsIgnoreCase(param)) paramClass = int.class;
        if ("long".equals(param) || "l".equalsIgnoreCase(param)) paramClass = long.class;
        if ("float".equals(param) || "f".equalsIgnoreCase(param)) paramClass = float.class;
        if ("double".equals(param) || "d".equalsIgnoreCase(param)) paramClass = double.class;

        if ("Boolean".equals(param) || "Bool".equals(param)) paramClass = Boolean.class;
        if ("Byte".equals(param)) paramClass = Byte.class;
        if ("Character".equalsIgnoreCase(param) || "C".equals(param)) paramClass = Character.class;
        if ("Short".equals(param)) paramClass = Short.class;
        if ("Integer".equals(param)) paramClass = Integer.class;
        if ("Long".equals(param)) paramClass = Long.class;
        if ("Float".equals(param)) paramClass = Float.class;
        if ("Double".equals(param)) paramClass = Double.class;

        if ("Class".equalsIgnoreCase(param)) paramClass = Class.class;
        if ("Number".equalsIgnoreCase(param)) paramClass = Number.class;
        if ("Object".equalsIgnoreCase(param)) paramClass = Object.class;
        if ("String".equalsIgnoreCase(param) || "str".equalsIgnoreCase(param)) paramClass = String.class;

        if ("HttpServletRequest".equalsIgnoreCase(param) || "req".equalsIgnoreCase(param) || "request".equalsIgnoreCase(param))
            paramClass = HttpServletRequest.class;
        if ("HttpServletResponse".equalsIgnoreCase(param) || "res".equalsIgnoreCase(param) || "response".equalsIgnoreCase(param))
            paramClass = HttpServletResponse.class;
        if ("ServletRequest".equalsIgnoreCase(param)) paramClass = ServletRequest.class;
        if ("ServletResponse".equalsIgnoreCase(param)) paramClass = ServletResponse.class;

        if ("javax.servlet.FilterChain".equalsIgnoreCase(param) || "FilterChain".equalsIgnoreCase(param)
                || "chain".equalsIgnoreCase(param)) {
            filter = true;
            paramClass = FilterChain.class;
        }

        if ( loadClass ) {
            if (paramClass == null) {
                try {
                    paramClass = Class.forName(param);
                    if (paramClass == null) {
                        setError("had trouble finding " + param + " after Class.forName got a null object");
                        return null;
                    }
                } catch (ClassNotFoundException e) {
                    setError("could not find " + param + " got a " + e.toString(), e);
                    return null;
                } catch (NoClassDefFoundError e) {
                    setError("could not find " + param + " got a " + e.toString(), e);
                    return null;
                }
            }
            if (paramClass == null) {
                setError("could not find class of type " + param);
                return null;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("parseClass found class " + paramClass + " for " + param);
        }
        return paramClass;
    }

    /**
     * Prepare the object for running by constructing and setting up method handles.
     */
    private void prepareRunObject(Class extraParam) {
        if (log.isDebugEnabled()) {
            log.debug("looking for class " + classStr);
        }
        Class runClass;
        try {
            runClass = Class.forName(classStr);
            if (runClass == null) {
                setError("had trouble finding " + classStr + " after Class.forName got a null object");
                return;
            }
        } catch (ClassNotFoundException e) {
            setError("could not find " + classStr + " got a " + e.toString(), e);
            return;
        } catch (NoClassDefFoundError e) {
            setError("could not find " + classStr + " got a " + e.toString(), e);
            return;
        }
        try {
            runConstructor = runClass.getConstructor((Class[]) null);
        } catch (NoSuchMethodException e) {
            setError("could not get constructor for " + classStr, e);
            return;
        }

        if (!runMethodUseDefaultParams) {
            if (log.isDebugEnabled()) {
                log.debug("looking for " + methodStr + " with specific params");
            }
            try {
                runMethod = runClass.getMethod(methodStr, runMethodParams);
            } catch (NoSuchMethodException e) {
                // do nothing
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("looking for " + methodStr + "(ServletRequest, ServletResponse)");
            }
            for (int i = 0; i < runMethodPossibleSignatures.length; i++) {
                Class[] runMethodPossibleSignature = runMethodPossibleSignatures[i];
                if (extraParam != null) {
                    runMethodPossibleSignature = new Class[]{runMethodPossibleSignature[0],
                            runMethodPossibleSignature[1], extraParam};
                }
                if (log.isDebugEnabled()) {
                    String possible = "";
                    for (int j = 0; j < runMethodPossibleSignature.length; j++) {
                        possible += (j > 0 ? "," : "") + runMethodPossibleSignature[j].getSimpleName();
                    }
                    log.debug("looking for " + methodStr + "(" + possible + ")");
                }
                try {
                    runMethod = runClass.getMethod(methodStr, runMethodPossibleSignature);
                    runMethodParams = runMethodPossibleSignature;
                    break;

                } catch (NoSuchMethodException e) {
                    // do nothing except be paranoid about resetting runMethodParams
                    runMethodParams = null;
                }
            }
            if (runMethod == null) {
                setError("could not find method with the name " + methodStr + " on " + classStr);
                return;
            }
        }

        Method[] methods = runClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if ("destroy".equals(method.getName()) && method.getParameterTypes().length == 0) {
                log.debug("found destroy methodStr");
                destroyMethod = method;
            }
            if ("init".equals(method.getName()) && method.getParameterTypes().length == 1 &&
                    ServletConfig.class.getName().equals(method.getParameterTypes()[0].getName())) {
                log.debug("found init methodStr");
                initMethod = method;
            }
            if ("init".equals(method.getName()) && method.getParameterTypes().length == 1 &&
                    FilterConfig.class.getName().equals(method.getParameterTypes()[0].getName())) {
                log.debug("found filter init methodStr");
                filterInitMethod = method;
            }
            if (initMethod != null && destroyMethod != null) break;
        }
        if (!newEachTime) {
            runClassInstance = fetchNewInstance();
        }
        valid = true;

    }

    private void invokeDestroy(Object runClassInstanceToDestroy) {
        if (runClassInstanceToDestroy != null && destroyMethod != null) {
            if (log.isDebugEnabled()) {
                log.debug("running " + classStr + ".destroy()");
            }
            try {
                destroyMethod.invoke(runClassInstanceToDestroy, (Object[]) null);
            } catch (IllegalAccessException e) {
                logInvokeException("destroy()", e);
            } catch (InvocationTargetException e) {
                logInvokeException("destroy()", e);
            }
        }
    }

    /**
     * Invokes the run method.
     * <p/>
     * Exceptions at invocation time are either rethrown as a ServletException or as thr original exception if we can
     * manage to do it.
     * <p/>
     * We don't log exceptions here, the container can do that.
     */
    private RewriteMatch invokeRunMethod(Object classInstanceToRun, HttpServletRequest httpServletRequest,
                                         HttpServletResponse httpServletResponse, FilterChain chain, Object[] matchObjs)
            throws ServletException, InvocationTargetException {
        if (log.isDebugEnabled()) {
            log.debug("running " + classStr + "." + getMethodSignature() + " ");
        }
        if (classInstanceToRun == null || runMethod == null) return null;
        RewriteMatch returned = null;
        Object[] params = null;

        if (runMethodParams != null && runMethodParams.length > 0) {
            params = new Object[runMethodParams.length];
            for (int i = 0; i < runMethodParams.length; i++) {
                Class runMethodParam = runMethodParams[i];
                String runMethodParamName = null;
                if ( runMethodParamNames != null && runMethodParamNames.length > i) {
                    runMethodParamName = runMethodParamNames[i];
                }
                Object param;

                if (runMethodParamName != null) {
                    log.debug("need parameter from request called " + runMethodParamName);
                    Object matchObj = httpServletRequest.getParameter(runMethodParamName);
                    param = getConvertedParam(runMethodParam, matchObj);

                } else if (runMethodParam.isAssignableFrom(HttpServletRequest.class)) {
                    param = httpServletRequest;

                } else if (runMethodParam.isAssignableFrom(HttpServletResponse.class)) {
                    param = httpServletResponse;

                } else if (runMethodParam.isAssignableFrom(FilterChain.class)) {
                    param = chain;

                } else {
                    Object matchObj = null;
                    if ( matchObjs != null && matchObjs.length > i ) matchObj = matchObjs[i];
                    param = getConvertedParam(runMethodParam, matchObj);
                }

                params[i] = param;
                if (log.isDebugEnabled()) {
                    log.debug("argument " + i + " (" + runMethodParam.getName() + "): " + param);
                }
            }
        }
        try {
            Object objReturned = runMethod.invoke(classInstanceToRun, (Object[]) params);
            if (objReturned != null) {
                // if we get a rewriteMatch object then return it for execution later
                if (objReturned instanceof RewriteMatch) {
                    returned = (RewriteMatch) objReturned;
                }
            }

        } catch (IllegalAccessException e) {
            if (log.isDebugEnabled()) log.debug(e);
            throw new ServletException(e);

        }
        return returned;
    }

    private Object getConvertedParam(Class runMethodParam, Object matchObj) {
        // see http://jakarta.apache.org/commons/beanutils/api/org/apache/commons/beanutils/MethodUtils.html
        // for how to handle methods better
        Object param = null;
        if (matchObj == null) {
            if (runMethodParam.isPrimitive()) {
                if (runMethodParam.equals(boolean.class)) param = Boolean.FALSE;
                else if (runMethodParam.equals(char.class)) param = new Character('\u0000');
                else if (runMethodParam.equals(byte.class)) param = new Byte((byte) 0);
                else if (runMethodParam.equals(short.class)) param = new Short((short) 0);
                else if (runMethodParam.equals(int.class)) param = new Integer(0);
                else if (runMethodParam.equals(long.class)) param = new Long(0L);
                else if (runMethodParam.equals(float.class)) param = new Float(0f);
                else if (runMethodParam.equals(double.class)) param = new Double(0d);
            }
        } else {
            if (runMethodParam.equals(Boolean.class) || runMethodParam.equals(boolean.class))
                param = Boolean.valueOf((String) matchObj);
            else if (runMethodParam.equals(Character.class) || runMethodParam.equals(char.class))
                param = new Character(((String) matchObj).charAt(0));
            else if (runMethodParam.equals(Byte.class) || runMethodParam.equals(byte.class))
                param = Byte.valueOf((String) matchObj);
            else if (runMethodParam.equals(Short.class) || runMethodParam.equals(short.class))
                param = Short.valueOf((String) matchObj);
            else if (runMethodParam.equals(Integer.class) || runMethodParam.equals(int.class))
                param = Integer.valueOf((String) matchObj);
            else if (runMethodParam.equals(Long.class) || runMethodParam.equals(long.class))
                param = Long.valueOf((String) matchObj);
            else if (runMethodParam.equals(Float.class) || runMethodParam.equals(float.class))
                param = Float.valueOf((String) matchObj);
            else if (runMethodParam.equals(Double.class) || runMethodParam.equals(double.class))
                param = Double.valueOf((String) matchObj);
            else if (matchObj != null &&
                    matchObj instanceof Throwable &&
                    runMethodParam.isAssignableFrom(matchObj.getClass()))
                param = matchObj;
            else {
                try {
                    // last attempt
                    param = runMethodParam.cast(matchObj);
                } catch (ClassCastException e) {
                    // do nothing
                }
            }
        }
        return param;
    }

    /**
     * Run the underlying destroy methodStr on the run classStr.
     */
    public void destroy() {
        initialised = false;
        valid = false;

        invokeDestroy(runClassInstance);

        // be paranoid and clean up all hooks to users classStr
        destroyMethod = null;
        runMethod = null;
        initMethod = null;
        filterInitMethod = null;
        runServletConfig = null;
        runConstructor = null;
        runClassInstance = null;

        methodStr = null;
        classStr = null;
        error = null;
    }

    /**
     * @see #execute(HttpServletRequest, HttpServletResponse, Object[], FilterChain)
     */
    public RewriteMatch execute(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws IOException, ServletException, InvocationTargetException {
        Object[] params = null;
        return execute(httpServletRequest, httpServletResponse, params, null);
    }

    public RewriteMatch execute(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                                StringMatchingMatcher matcher, ConditionMatch conditionMatch, FilterChain chain)
            throws IOException, ServletException, InvocationTargetException {
        int matches = 0;
        int condMatches = 0;
        if (matcher != null && matcher.isFound()) {
            matches = matcher.groupCount();
        }
        if (conditionMatch != null) {
            StringMatchingMatcher condMatcher = conditionMatch.getMatcher();
            if (condMatcher != null && condMatcher.isFound()) {
                condMatches = condMatcher.groupCount();
            }
        }
        String[] allMatches = null;
        if ((matches + condMatches) > 0) {
            allMatches = new String[matches + condMatches];
            if (matcher != null && matches > 0) {
                for (int i = 0; i < matches; i++) {
                    allMatches[i] = matcher.group(i + 1); // note, good groups start from 1
                }
            }
            if (conditionMatch != null && condMatches > 0) {
                for (int i = 0; i < condMatches; i++) {
                    allMatches[i] = conditionMatch.getMatcher().group(i);
                }
            }
        }
        return execute(httpServletRequest, httpServletResponse, allMatches, chain);
    }

    /**
     * Will invoke the instance created in initialise.
     *
     * @param httpServletRequest
     * @param httpServletResponse
     */
    public RewriteMatch execute(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                                Throwable throwable)
            throws IOException, ServletException, InvocationTargetException {
        Object[] params = new Object[]{throwable};
        return execute(httpServletRequest, httpServletResponse, params, null);
    }

    public RewriteMatch execute(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                                Object[] params) throws IOException, ServletException, InvocationTargetException {
        return execute(httpServletRequest, httpServletResponse, params, null);
    }

    public RewriteMatch execute(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                                Object[] params, FilterChain chain)
            throws IOException, ServletException, InvocationTargetException {
        if (!initialised) {
            log.debug("not initialised skipping");
            return null;
        }
        if (!valid) {
            log.debug("not valid skipping");
            return null;
        }
        RewriteMatch returned;
        try {
            if (newEachTime) {
                Object newRunClassInstance = fetchNewInstance();
                returned = invokeRunMethod(newRunClassInstance, httpServletRequest, httpServletResponse, chain, params);
                invokeDestroy(newRunClassInstance);
            } else {
                returned = invokeRunMethod(runClassInstance, httpServletRequest, httpServletResponse, chain, params);
            }
        } catch (ServletException e) {
            httpServletRequest.setAttribute("javax.servlet.error.exception", e);
            throw e;
        }
        return returned;
    }

    private void logInvokeException(String methodStr, Exception e) {
        Throwable cause = e.getCause();
        if (cause == null) {
            setError("when invoking " + methodStr + " on " + classStr
                    + " got an " + e.toString(), e);
        } else {
            setError("when invoking " + methodStr + " on " + classStr
                    + " got an " + e.toString() + " caused by " + cause.toString(), cause);
        }
    }

    /**
     * Get a new instance of the classStr we want to run and init if required.
     *
     * @return the new instance
     */
    private Object fetchNewInstance() {
        Object obj;
        log.debug("getting new instance of " + classStr);
        try {
            obj = runConstructor.newInstance((Object[]) null);
        } catch (InstantiationException e) {
            logInvokeException("constructor", e);
            return null;
        } catch (IllegalAccessException e) {
            logInvokeException("constructor", e);
            return null;
        } catch (InvocationTargetException e) {
            logInvokeException("constructor", e);
            return null;
        }
        if (initMethod != null) {
            log.debug("about to run init(ServletConfig) on " + classStr);
            Object[] args = new Object[1];
            args[0] = runServletConfig;
            try {
                initMethod.invoke(obj, args);
            } catch (IllegalAccessException e) {
                logInvokeException("init(ServletConfig)", e);
                return null;
            } catch (InvocationTargetException e) {
                logInvokeException("init(ServletConfig)", e);
                return null;
            }
        }
        if (filterInitMethod != null) {
            log.debug("about to run init(FilterConfig) on " + classStr);
            Object[] args = new Object[1];
            args[0] = runServletConfig;
            try {
                filterInitMethod.invoke(obj, args);
            } catch (IllegalAccessException e) {
                logInvokeException("init(FilterConfig)", e);
                return null;
            } catch (InvocationTargetException e) {
                logInvokeException("init(FilterConfig)", e);
                return null;
            }
        }
        return obj;
    }

    public String getError() {
        return error;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isInitialised() {
        return initialised;
    }

    /**
     * The name of the classStr that will be run for each rule match.
     *
     * @return String eg, org.tuckey.YellowObject
     */
    public String getClassStr() {
        return classStr;
    }

    /**
     * The name of the methodStr that will be run for each rule match.
     *
     * @return String eg, setDate
     */
    public String getMethodStr() {
        return methodStr;
    }

    /**
     * The name of  the method signature ie, setDate(java.util.Date, int).  Includes fully qualified object names
     * for paramters.
     */
    public String getMethodSignature() {
        if (methodStr == null) return null;
        StringBuffer sb = new StringBuffer(methodStr);
        if (runMethodParams != null) {
            for (int i = 0; i < runMethodParams.length; i++) {
                Class runMethodParam = runMethodParams[i];
                if (runMethodParam == null) continue;
                if (i == 0) sb.append("(");
                if (i > 0) sb.append(", ");
                sb.append(runMethodParam.getName());
                if (i + 1 == runMethodParams.length) sb.append(")");
            }
        }
        return sb.toString();
    }


    public boolean isNewEachTime() {
        return newEachTime;
    }

    public void setNewEachTime(boolean newEachTime) {
        this.newEachTime = newEachTime;
    }

    /**
     * Gets a handle on the instance of the class run is running.
     * <p/>
     * If newEachTime is set to true this will always return null.
     */
    public Object getRunClassInstance() {
        return runClassInstance;
    }

    public void addInitParam(String name, String value) {
        if (name != null) {
            initParams.put(name, value);
        }
    }

    public String getInitParam(String paramName) {
        return (String) initParams.get(paramName);
    }

    public void setClassStr(String classStr) {
        this.classStr = classStr;
    }

    public void setMethodStr(String methodStr) {
        this.methodStr = methodStr;
    }

    public static void setLoadClass(boolean loadClass) {
        Run.loadClass = loadClass;
    }

    public void setError(String error, Throwable t) {
        this.error = error;
        log.error(error, t);
    }

    public void setError(String error) {
        this.error = error;
        log.error(error);
    }

    public String getDisplayName() {
        return "Run " + id;
    }

    public boolean isFilter() {
        return filter;
    }
}
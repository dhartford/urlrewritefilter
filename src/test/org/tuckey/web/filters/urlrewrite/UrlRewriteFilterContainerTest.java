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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.cactus.FilterTestCase;
import org.apache.cactus.WebRequest;
import org.apache.cactus.WebResponse;
import org.apache.cactus.server.RequestDispatcherWrapper;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import org.tuckey.web.filters.urlrewrite.utils.Log;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Paul Tuckey
 * @version $Revision: 25 $ $Date: 2006-09-05 09:48:23 +1200 (Tue, 05 Sep 2006) $
 */
public class UrlRewriteFilterContainerTest extends FilterTestCase {

    private UrlRewriteFilter filter = new UrlRewriteFilter();
    private String CONTENT = "<p>some content</p>";

    public void setUp() {
        config.setInitParameter("logLevel", "sysout:TRACE");
        Log.setLevel("sysout:TRACE");
        filter.init(config);
    }

    public void tearDown() {
        filter.destroy();
    }


    /**
     * mock chain for use with tests
     */
    private FilterChain testFilterChain = new FilterChain() {
        public void doFilter(ServletRequest theRequest,
                             ServletResponse theResponse) throws IOException {
            PrintWriter writer = theResponse.getWriter();
            writer.print(CONTENT);
            writer.close();
        }
    };


    public static Test suite() {
        return new TestSuite(UrlRewriteFilterContainerTest.class);
    }

    public class TestResponseWrapper extends HttpServletResponseWrapper {
        public String redirectToUrl = "not yet!";
        private int status;

        public TestResponseWrapper(HttpServletResponse httpServletResponse) {
            super(httpServletResponse);
        }

        public void sendRedirect(String to) {
            this.redirectToUrl = to;
        }

        public void setStatus(int i) {
            status = i;
            super.setStatus(i);
        }

        public int getStatus() {
            return status;
        }

        public String encodeRedirectURL(String s) {
            return super.encodeRedirectURL(s) + ";jsess";
        }
    }

    public class TestRequestWrapper extends HttpServletRequestWrapper {
        TestRequestDispatcherWrapper dispatcher;
        public String dispatcherUrl = "not set";
        public String contextPath = "not set";

        public TestRequestWrapper(HttpServletRequest httpServletRequest) {
            super(httpServletRequest);
        }

        public RequestDispatcher getRequestDispatcher(String url) {
            dispatcherUrl = url;
            dispatcher = new TestRequestDispatcherWrapper(super.getRequestDispatcher(url));
            return dispatcher;
        }

        public String getContextPath() {
            return contextPath;
        }

        public void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }
    }

    public class TestRequestDispatcherWrapper extends RequestDispatcherWrapper {
        boolean forwarding = false;
        boolean including = false;

        public TestRequestDispatcherWrapper(RequestDispatcher requestDispatcher) {
            super(requestDispatcher);
        }

        public void forward(ServletRequest servletRequest, ServletResponse servletResponse)
                throws ServletException, IOException {
            forwarding = true;
            super.forward(servletRequest, servletResponse);
        }

        public void include(ServletRequest servletRequest, ServletResponse servletResponse)
                throws ServletException, IOException {
            including = true;
            super.include(servletRequest, servletResponse);
        }
    }


    /**
     *
     */
    public void beginProduct(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/products/987", null, null);
    }

    public void testProduct() throws ServletException, IOException, InterruptedException {
        Thread.sleep(30000);
        filter.doFilter(request, response, testFilterChain);
    }

    public void endProduct(WebResponse theResponse) {
        assertEquals("product 987", theResponse.getText());
    }

    /**
     *
     */
    public void beginSimpleDistEx(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/test/status/", null, null);
    }

    public void testSimpleDistEx() throws ServletException, IOException {
        TestResponseWrapper testResponseWrapper = new TestResponseWrapper(response);
        TestRequestWrapper testRequestWrapper = new TestRequestWrapper(request);
        testRequestWrapper.setContextPath("/blah");
        filter.doFilter(testRequestWrapper, testResponseWrapper, testFilterChain);
        assertEquals("/blah/rewrite-status", testResponseWrapper.redirectToUrl);
    }

    /**
     *
     */
    public void beginBasicSets(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/settest/674", null, null);
    }

    public void testBasicSets() throws ServletException, IOException {
        TestRequestWrapper testRequestWrapper = new TestRequestWrapper(request);
        filter.doFilter(testRequestWrapper, response, testFilterChain);
        assertTrue(response.containsHeader("cache-control"));
        assertEquals("hello!", request.getSession().getAttribute("testsession"));
    }


    /**
     *
     */
    public void beginMultipleProduct(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/multiple/products/987", null, null);
    }

    public void testMultipleProduct() throws ServletException, IOException {
        filter.doFilter(request, response, testFilterChain);
    }

    public void endMultipleProduct(WebResponse theResponse) {
        assertEquals("product 987", theResponse.getText());
    }

    /**
     *
     */
    public void beginNullTo(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/hideme/anb.jsp;dsaddd?asdasds#sdsfd", null, null);
    }

    public void testNullTo() throws ServletException, IOException {
        filter.doFilter(request, response, testFilterChain);
    }

    public void endNullTo(WebResponse theResponse) {
        assertEquals("should have status set", 403, theResponse.getStatusCode());
        assertFalse("should not output above content", CONTENT.equals(StringUtils.trim(theResponse.getText())));
    }

    /**
     *
     */
    public void beginYear(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/time/year/current", null, null);
    }

    public void testYear() throws ServletException, IOException {
        filter.doFilter(request, response, testFilterChain);
    }

    public void endYear(WebResponse theResponse) {
        assertEquals("echo yearisbetween1970and3000", theResponse.getText());
    }

    /**
     *
     */
    public void beginTestAxis(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/services/blah?qwerty", null, null);
    }

    public void testTestAxis() throws ServletException, IOException {
        TestResponseWrapper testResponseWrapper = new TestResponseWrapper(response);
        filter.doFilter(request, testResponseWrapper, testFilterChain);
        assertEquals("/axis/services/blah?qwerty", testResponseWrapper.redirectToUrl);
    }


    /**
     *
     */
    public void beginTestErik(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/eriktest/hi.ho", null, null);
        theRequest.addHeader("host", "blah.com");
    }

    public void testTestErik() throws ServletException, IOException {
        TestResponseWrapper testResponseWrapper = new TestResponseWrapper(response);
        filter.doFilter(request, testResponseWrapper, testFilterChain);
        assertEquals("http://www.example.com/context/hi.ho", testResponseWrapper.redirectToUrl);
    }

    /**
     *
     */
    public void beginTestEncode(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/went%20to%20bahamas/", null, null);
    }

    public void testTestEncode() throws ServletException, IOException {
        TestResponseWrapper testResponseWrapper = new TestResponseWrapper(response);
        filter.doFilter(request, testResponseWrapper, testFilterChain);
        assertEquals("/bahamas/;jsess", testResponseWrapper.redirectToUrl);
    }

    /**
     * note, had trouble keeping true utf (multi byte) chars as cvs buggers them up!
     */
    public void beginTestUtf(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/utf/F�tel'ha�volap�k/", null, null);
    }

    public void testTestUtf() throws ServletException, IOException {
        TestResponseWrapper testResponseWrapper = new TestResponseWrapper(response);
        filter.doFilter(request, testResponseWrapper, testFilterChain);
        assertEquals("/utf-redir/F�tel'ha�volap�k/", testResponseWrapper.redirectToUrl);
    }

    /**
     *
     */
    public void beginSimpleRun(WebRequest theRequest) {
        theRequest.setURL("blah.org", "", "/run/test/test1", null, null);
    }

    public void testSimpleRun() throws ServletException, IOException {
        assertTrue("should be inited", TestRunObj.isInitCalled());

        TestResponseWrapper testResponseWrapper = new TestResponseWrapper(response);
        filter.doFilter(request, testResponseWrapper, testFilterChain);
    }

    public void endSimpleRun(WebResponse theResponse) {
        assertEquals("this is " + TestRunObj.class.getName(), theResponse.getText());
    }

}
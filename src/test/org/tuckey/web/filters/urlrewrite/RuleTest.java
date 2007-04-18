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

import junit.framework.TestCase;
import org.tuckey.web.filters.urlrewrite.utils.Log;
import org.tuckey.web.testhelper.MockRequest;
import org.tuckey.web.testhelper.MockResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * @author Paul Tuckey
 * @version $Revision: 49 $ $Date: 2006-12-08 10:09:07 +1300 (Fri, 08 Dec 2006) $
 */
public class RuleTest extends TestCase {

    MockResponse response;
    MockRequest request;

    public void setUp() {
        Log.setLevel("TRACE");
        response = new MockResponse();
        request = new MockRequest();
    }

    public void testRule01() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("simple(ass)");
        rule.setTo("$1simple");
        rule.initialise(null);
        MockRequest request = new MockRequest("simpleass");
        NormalRewrittenUrl rewrittenUrl = (NormalRewrittenUrl) rule.matches(request.getRequestURI(), request, response);

        assertEquals("forward should be default type", "forward", rule.getToType());
        assertEquals("asssimple", rewrittenUrl.getTarget());
        assertTrue("Should be a forward", rewrittenUrl.isForward());
    }

    public void testRuleNullTo() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("null");
        rule.initialise(null);
        SetAttribute setAttribute1 = new SetAttribute();
        setAttribute1.setType("status");
        setAttribute1.setValue("302");
        rule.addSetAttribute(setAttribute1);
        rule.initialise(null);

        MockRequest request = new MockRequest("from");
        NormalRewrittenUrl rewrittenUrl = (NormalRewrittenUrl) rule.matches(request.getRequestURI(), request, response);
        assertEquals(302, response.getStatus());
        assertTrue(rewrittenUrl.isStopFilterChain());

    }

    public void testRuleInclude() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");
        rule.setToType("pre-include");
        rule.initialise(null);
        request.setRequestURI("from");
        NormalRewrittenUrl rewrittenUrl = (NormalRewrittenUrl) rule.matches(request.getRequestURI(), request, response);

        assertEquals("pre-include", rule.getToType());
        assertTrue("Should be an pre include", rewrittenUrl.isPreInclude());
    }

    public void testRuleBackRef() throws IOException, ServletException, InvocationTargetException {
        Condition c = new Condition();
        c.setName("hdr");
        c.setValue("aaa([a-z]+)cc(c)");
        NormalRule rule = new NormalRule();
        rule.setFrom("([a-z])rom");
        rule.setTo("from match: $1, backref1: %1, backref2: %2, bad backref: %a % %99 %%88, escaped backref: \\%2");
        rule.addCondition(c);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.setHeader("hdr", "aaafffccc");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);

        assertEquals("Should have replaced back reference",
                "from match: f, backref1: fff, backref2: c, bad backref: %a % 9 %8, escaped backref: %2", rewrittenUrl.getTarget());

        assertTrue(rule.isToContainsBackReference());
        assertFalse(rule.isToContainsVariable());
    }

    public void testRuleBackRefWildcard() throws IOException, ServletException, InvocationTargetException {
        Condition c = new Condition();
        c.setName("hdr");
        c.setValue("aaa-*-cc-*");
        NormalRule rule = new NormalRule();
        rule.setFrom("*rom");
        rule.setTo("from match: $1, backref1: %1, backref2: %2, bad backref: %a % %99 %%88, escaped backref: \\%2");
        rule.setMatchType("wildcard");
        rule.addCondition(c);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.setHeader("hdr", "aaa-fff-cc-c");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);

        assertEquals("Should have replaced back reference",
                "from match: f, backref1: fff, backref2: c, bad backref: %a % 9 %8, escaped backref: %2", rewrittenUrl.getTarget());

        assertTrue(rule.isToContainsBackReference());
        assertFalse(rule.isToContainsVariable());
    }

    /**
     * here's my urlrewriter.xml :
     * <condition type="server-name">^([^.]+)\.domain\.com</condition>
     * <from>^/(.*)</from>
     * <to type="redirect" last="true">/%1/$1</to>
     * <p/>
     * Now the url with host header 'http://test.domain.com' should be
     * redirected to 'http://domain.com/test'. But for some reason it doesnt
     * replace the %1 with 'test'. The redirected url is :
     * http://domain.com/%1
     */
    public void testRuleBackRefHost() throws IOException, ServletException, InvocationTargetException {
        Condition c = new Condition();
        c.setType("server-name");
        c.setValue("^([^.]+)\\.domain\\.com");
        NormalRule rule = new NormalRule();
        rule.setFrom("^/(.*)");
        rule.setTo("/%1/$1");
        rule.setToType("redirect");
        rule.setToLast("true");
        rule.addCondition(c);
        rule.initialise(null);
        MockRequest request = new MockRequest("/from");
        request.setServerName("server.domain.com");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);

        assertEquals("Should have replaced back reference",
                "/server/from", rewrittenUrl.getTarget());

        assertTrue(rule.isToContainsBackReference());

    }

    public void testRuleBackRefMixed() throws IOException, ServletException, InvocationTargetException {
        Condition c = new Condition();
        c.setName("hdr");
        c.setValue("aaa([a-z]+)ccc");
        NormalRule rule = new NormalRule();
        rule.setFrom("([a-z])rom");
        rule.setTo("$1 to %{remote-host} %1 here \\%2");
        rule.addCondition(c);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.setRemoteHost("server!");
        request.addHeader("hdr", "aaabbbccc");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);

        assertTrue(rule.isToContainsBackReference());
        assertTrue(rule.isToContainsVariable());

        assertEquals("Should have replaced back reference", "f to server! bbb here %2", rewrittenUrl.getTarget());
    }

    public void testRuleBackRefVar() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("start ctx: %{context-path}, hdr: %{header:bananna} %{header:}%{::}%{%{}, escaped var: \\%{ignoreme!}, bad var: %{} %{wibble} end");
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.setContextPath("ctxpath");
        request.setRemoteHost("server!");
        request.addHeader("bananna", "bender");
        request.setServerPort(90210);
        NormalRewrittenUrl rewrittenUrl = (NormalRewrittenUrl) rule.matches(request.getRequestURI(), request, response);

        assertFalse(rule.isToContainsBackReference());
        assertTrue(rule.isToContainsVariable());

        assertEquals("forward should be default type", "forward", rule.getToType());
        assertEquals("start ctx: ctxpath, hdr: bender , escaped var: %{ignoreme!}, bad var:   end", rewrittenUrl.getTarget());
        assertTrue("Should be a forward", rewrittenUrl.isForward());
    }

    public void testRuleBadCondition() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");
        Condition condition = new Condition();
        condition.setType("port");
        condition.setValue("aaa");
        rule.addCondition(condition);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull(rewrittenUrl);
    }

    public void testRuleConditionsOr() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");
        Condition condition = new Condition();
        condition.setType("port");
        condition.setValue("90");
        condition.setNext("or");
        rule.addCondition(condition);
        Condition condition2 = new Condition();
        condition2.setType("port");
        condition2.setValue("99");
        rule.addCondition(condition2);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.setServerPort(88);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull("should not be rewritten", rewrittenUrl);

        request.setServerPort(90);
        RewrittenUrl rewrittenUrl2 = rule.matches(request.getRequestURI(), request, response);
        assertEquals("should not be rewritten", "to", rewrittenUrl2.getTarget());

        request.setServerPort(99);
        RewrittenUrl rewrittenUrl3 = rule.matches(request.getRequestURI(), request, response);
        assertEquals("should not be rewritten", "to", rewrittenUrl3.getTarget());
    }

    public void testRuleConditionsAnd() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");
        Condition condition = new Condition();
        condition.setType("port");
        condition.setValue("90");
        rule.addCondition(condition);
        Condition condition2 = new Condition();
        condition2.setType("character-encoding");
        condition2.setValue("utf8");
        rule.addCondition(condition2);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.setServerPort(88);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull("should not be rewritten", rewrittenUrl);

        request.setServerPort(90);
        RewrittenUrl rewrittenUrl2 = rule.matches(request.getRequestURI(), request, response);
        assertNull("should not be rewritten", rewrittenUrl2);

        request.setCharacterEncoding("utf8");
        RewrittenUrl rewrittenUrl3 = rule.matches(request.getRequestURI(), request, response);
        assertEquals("should be rewritten", "to", rewrittenUrl3.getTarget());
    }

    public void testRuleConditionsReplaceAll() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("-");
        rule.setTo("_");
        Condition condition = new Condition();
        condition.setType("request-uri");
        condition.setValue("/hi-something-to-to.html");
        rule.addCondition(condition);
        rule.initialise(null);
        MockRequest request = new MockRequest("/hi-something-to-to.html");
        request.setServerPort(88);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("/hi_something_to_to.html", rewrittenUrl.getTarget());
    }

    public void testRuleBadRegex() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("fro[m");
        rule.setTo("to");
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull(rewrittenUrl);
    }

    public void testRuleCaseInsensitive() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("fRom");
        rule.setTo("to");
        Condition condition = new Condition();
        condition.setName("agent");
        condition.setValue("aAa");
        rule.addCondition(condition);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.addHeader("agent", "aaa");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals(rewrittenUrl.getTarget(), "to");
    }

    public void testRuleRewriteReq() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setEncodeToUrl(true);
        rule.setTo("to");
        rule.setToType("permanent-redirect");
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        NormalRewrittenUrl rewrittenUrl = (NormalRewrittenUrl) rule.matches(request.getRequestURI(), request, response);
        assertTrue(rewrittenUrl.isEncode());
        assertTrue(rewrittenUrl.isPermanentRedirect());
    }

    public void testRuleNoInit() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");
        MockRequest request = new MockRequest("from");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull(rewrittenUrl);
    }

    public void testRuleNotEnabled() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");
        rule.setEnabled(false);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull(rewrittenUrl);
    }

    public void testRule02() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("/countries/([a-z]+)/");
        rule.setTo("/countries/index.jsp?c=$1");
        rule.setToType("redirect");
        rule.initialise(null);

        MockRequest request = new MockRequest("/countries/australia/");
        NormalRewrittenUrl rewrittenUrl = (NormalRewrittenUrl) rule.matches(request.getRequestURI(), request, response);
        MockRequest request2 = new MockRequest("/blah/");
        NormalRewrittenUrl rewrittenUrl2 = (NormalRewrittenUrl) rule.matches(request2.getRequestURI(), request2, response);

        assertEquals("/countries/index.jsp?c=australia", rewrittenUrl.getTarget());
        assertEquals("redirect", rule.getToType());
        assertTrue(rewrittenUrl.isRedirect());
        assertNull("Rule should not match", rewrittenUrl2);
    }


    public void testRuleCondition() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");
        Condition condition = new Condition();
        condition.setType("port");
        condition.setValue("5050");
        rule.addCondition(condition);
        rule.initialise(null);
        MockRequest request = new MockRequest("from");
        request.setServerPort(5050);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("to", rewrittenUrl.getTarget());
    }

    public void testRuleErikBug() throws IOException, ServletException, InvocationTargetException {
        // see http://www.erikisaksson.com/blog/2004/07/27/1090952764000.html
        NormalRule rule = new NormalRule();
        rule.setFrom("^(.*)$");
        rule.setTo("http://short.com/context$1");
        Condition condition = new Condition();
        condition.setName("host");
        condition.setOperator("notequal");
        condition.setValue("short\\.com");
        rule.addCondition(condition);
        rule.initialise(null);
        MockRequest request = new MockRequest("/blahurl");
        request.addHeader("host", "short.com");

        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("no rewriting should be necessary", null, rewrittenUrl);

        request.addHeader("host", "www.notexample.com");
        RewrittenUrl rewrittenUrl2 = rule.matches(request.getRequestURI(), request, response);
        assertEquals("http://short.com/context/blahurl", rewrittenUrl2.getTarget());
    }


    public void testRule08() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("/$");
        rule.setTo("/opencms/opencms/index.html");
        rule.initialise(null);

        MockRequest request = new MockRequest("/");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("/opencms/opencms/index.html", rewrittenUrl.getTarget());

        request = new MockRequest("/xyz");
        rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull(rewrittenUrl);
    }


    public void testRuleTrailingSlashRedirect() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("/~quux/foo$");
        rule.setTo("/~quux/foo/");
        rule.initialise(null);

        MockRequest request = new MockRequest("/~quux/foo");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("/~quux/foo/", rewrittenUrl.getTarget());
    }

    public void testRule10() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("/products/([0-9]+)");
        rule.setTo("/index.jsp?product_id=$1");
        rule.initialise(null);
        MockRequest request = new MockRequest("/products/105874");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("/index.jsp?product_id=105874", rewrittenUrl.getTarget());
    }


    public void testRule11() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("^/([a-z]+)/$");
        rule.setTo("/$1/index.jsp");
        rule.initialise(null);
        MockRequest request = new MockRequest("/dir/");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("/dir/index.jsp", rewrittenUrl.getTarget());
    }


    public void testFailedRule1() throws IOException, ServletException, InvocationTargetException {
        String uri = "/article/blah/";
        NormalRule rule = new NormalRule();
        rule.setFrom("/article/([0-9]+)/([0-9]+)/([0-9]+)/([a-zA-Z0-9]+)/");
        rule.setTo("/article/index.jsp?year=$1&month=$2&day=$3&code=$4");
        rule.initialise(null);

        MockRequest request = new MockRequest(uri);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertNull(rewrittenUrl);
    }


    public void testRuleConditionsUserInRole() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("from");
        rule.setTo("to");

        Condition condition = new Condition();
        condition.setType("user-in-role");
        condition.setValue("admin");
        condition.setOperator("notequal");
        rule.addCondition(condition);

        Condition condition2 = new Condition();
        condition2.setType("user-in-role");
        condition2.setValue("boss");
        condition2.setOperator("notequal");
        rule.addCondition(condition2);
        rule.initialise(null);

        MockRequest request = new MockRequest("from");
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("should be rewritten", "to", rewrittenUrl.getTarget());

        request.addRole("boss");
        RewrittenUrl rewrittenUrl2 = rule.matches(request.getRequestURI(), request, response);
        assertNull("should not be rewritten", rewrittenUrl2);
    }

    /**
     * <rule>
     * <name>Bild redirect test</name>
     * <from>logo.gif</from>
     * <to type="permanent-redirect">http://de010009.de.ina.com:8080/urlrewrite/artifact_type.gif</to>
     * </rule>
     */
    public void testBlindRedirect() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("^/logo\\.gif$");
        rule.setToType("permanent-redirect");
        rule.setTo("http://de010009\\.de\\.ina\\.com:8080/urlrewrite/artifact_type\\.gif");
        rule.initialise(null);
        MockRequest request = new MockRequest("/logo.gif");
        NormalRewrittenUrl rewrittenUrl = (NormalRewrittenUrl) rule.matches(request.getRequestURI(), request, response);
        assertEquals("http://de010009.de.ina.com:8080/urlrewrite/artifact_type.gif", rewrittenUrl.getTarget());
        assertTrue(rewrittenUrl.isPermanentRedirect());
        assertFalse(rewrittenUrl.isEncode());
    }


    public void testRuleHighChar() throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom("/aa\\?a=(.*)");
        rule.setEncodeToUrl(true);
        rule.setTo("$1");
        rule.setToType("permanent-redirect");
        rule.initialise(null);
        String highStr = new String("\u00F6\u236a\u2E88".getBytes(), "UTF8");
        MockRequest request = new MockRequest("/aa?a=" + highStr);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals(highStr, rewrittenUrl.getTarget());
    }

    /**
     * <rule>
     * <condition type="cookie" name="SAJKLHDSKJHDSKJHD" />
     * <from>/a\.jsp</from>
     * <to>/b.jsp</to>
     * </rule>
     * <p/>
     * (Which I think it means: "if the user asks for a.jsp and the cookie
     * SAJKLHDSKJHDSKJHD is not set, redirect him to b.jsp") the rule stops
     * working. Maybe it's because the cookie is null instead of an empty
     * string, I don't know.
     */
    public void testRuleCookieEmpty() throws IOException, ServletException, InvocationTargetException {
        Condition c = new Condition();
        c.setType("cookie");
        c.setName("abcdef");
        NormalRule rule = new NormalRule();
        rule.setFrom("/a\\.jsp");
        rule.setTo("/b.jsp");
        rule.addCondition(c);
        rule.initialise(null);
        RewrittenUrl rewrittenUrl = rule.matches("/a.jsp", request, response);
        assertTrue(request.getCookies() == null);
        assertTrue(rewrittenUrl == null);

        c = new Condition();
        c.setType("cookie");
        c.setName("abcdef");
        c.setOperator("notequal");
        rule = new NormalRule();
        rule.setFrom("/a\\.jsp");
        rule.setTo("/b.jsp");
        rule.addCondition(c);
        rule.initialise(null);
        rewrittenUrl = rule.matches("/a.jsp", request, response);
        assertTrue(request.getCookies() == null);
        assertEquals("/b.jsp", rewrittenUrl.getTarget());

    }

    public void testRuleWildcard1() throws IOException, ServletException, InvocationTargetException {
        wildcardRuleTestHelper("/*/", "/b/", "/a12sfg-+\\?#/", "/b/");
        wildcardRuleTestHelper("/*/*/", "/b/c/", "/z23434/-+=asd/", "/b/c/");
        wildcardRuleTestHelper("/*/*/", "/to/", "/a/b/c/", "/a/b/c/");
        wildcardRuleTestHelper("/**/", "/to/", "/a/b/c", "/a/b/c");
        wildcardRuleTestHelper("/**/", "/to/", "/a/b/c/", "/to/");
        wildcardRuleTestHelper("**.html", "/to/", "/a/b/c/sldwe.html", "/to/");
        wildcardRuleTestHelper("*.html", "/to/", "/a/b/c/sldwe.html", "/a/b/c/sldwe.html");
        wildcardRuleTestHelper("/a/b/c/*.html", "/to/", "/a/b/c/sldwe.html", "/to/");
    }


    public void testRuleWildcard2() throws IOException, ServletException, InvocationTargetException {
        wildcardRuleTestHelper("/*.h", "/$1", "/a.h", "/a");
        wildcardRuleTestHelper("/**", "/d/$1", "/a/b/c", "/d/a/b/c");
        // test same with conds
        wildcardRuleTestHelper("/*/a/*/aa", "/a/$2$1", "/w/a/c/aa", "/a/cw");
    }


    public void testRuleWildcardCond() throws IOException, ServletException, InvocationTargetException {
        MockRequest request = new MockRequest("/from");

        request.setServerName("dev.googil.com");
        ruleWildcardCondHelper("server-name", "dev*", request);

        request.setContentType("image/jpeg");
        ruleWildcardCondHelper("content-type", "image/*", request);

        request.setContextPath("/a/b/c");
        ruleWildcardCondHelper("context-path", "/a/**", request);

    }


    public void ruleWildcardCondHelper(String type, String value, MockRequest request) throws IOException, ServletException, InvocationTargetException {
        Condition condition = new Condition();
        condition.setType(type);
        condition.setValue(value);

        NormalRule rule = new NormalRule();
        rule.setFrom("/from");
        rule.setMatchType(" wildcard    ");
        rule.setTo("/to");
        rule.addCondition(condition);
        rule.initialise(null);

        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals("/to", rewrittenUrl == null ? "/from" : rewrittenUrl.getTarget());

    }

    public void wildcardRuleTestHelper(String from, String to, String req, String assertEq)
            throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom(from);
        rule.setMatchType(" wildcard    ");
        rule.setTo(to);
        rule.initialise(null);
        MockRequest request = new MockRequest(req);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals(assertEq, rewrittenUrl == null ? req : rewrittenUrl.getTarget());
    }

    public void wildcardRuleTestHelperCond(String from, String to, List conditions, String req, String assertEq)
            throws IOException, ServletException, InvocationTargetException {
        NormalRule rule = new NormalRule();
        rule.setFrom(from);
        rule.setMatchType(" wildcard    ");
        rule.setTo(to);
        if (conditions != null) {
            for (int i = 0; i < conditions.size(); i++) {
                Condition c = (Condition) conditions.get(i);
                rule.addCondition(c);
            }
        }
        rule.initialise(null);
        MockRequest request = new MockRequest(req);
        RewrittenUrl rewrittenUrl = rule.matches(request.getRequestURI(), request, response);
        assertEquals(assertEq, rewrittenUrl == null ? req : rewrittenUrl.getTarget());
    }


    public void testRuleSetAttr() throws IOException, ServletException, InvocationTargetException {
        Condition c = new Condition();
        c.setType("param");
        c.setName("param1");
        c.setValue("foo");

        SetAttribute s = new SetAttribute();
        NormalRule rule = new NormalRule();
        s.setType("parameter");
        s.setName("param1");
        s.setValue("bar");
        rule.setFrom("/blah");
        rule.setTo("/to");
        rule.addCondition(c);
        rule.addSetAttribute(s);
        rule.initialise(null);
        MockRequest request = new MockRequest("/blah");
        request.addParameter("param1", "foo");
        UrlRewriteWrappedRequest urlRewriteWrappedRequest = new UrlRewriteWrappedRequest(request);

        rule.matches(request.getRequestURI(), urlRewriteWrappedRequest, response);

        assertEquals("forward should be default type", "forward", rule.getToType());
        assertEquals("bar", urlRewriteWrappedRequest.getParameter("param1"));
    }


}
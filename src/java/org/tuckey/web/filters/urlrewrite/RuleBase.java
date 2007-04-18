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

import org.tuckey.web.filters.urlrewrite.utils.Log;
import org.tuckey.web.filters.urlrewrite.utils.RegexPattern;
import org.tuckey.web.filters.urlrewrite.utils.StringMatchingMatcher;
import org.tuckey.web.filters.urlrewrite.utils.StringMatchingPattern;
import org.tuckey.web.filters.urlrewrite.utils.StringMatchingPatternSyntaxException;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import org.tuckey.web.filters.urlrewrite.utils.WildcardPattern;
import org.tuckey.web.filters.urlrewrite.extend.RewriteMatch;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.InvocationTargetException;

/**
 * Defines a rule that can be run against an incoming request.
 *
 * @author Paul Tuckey
 * @version $Revision: 36 $ $Date: 2006-09-19 18:32:39 +1200 (Tue, 19 Sep 2006) $
 */
public class RuleBase implements Runnable {

    private static Log log = Log.getLog(RuleBase.class);

    protected int id;

    private boolean enabled = true;

    private boolean fromCaseSensitive;
    protected boolean initialised;
    protected boolean valid;

    protected String name;
    private String note;
    protected String from;
    protected String to;
    private boolean toEmpty;
    private String matchType;
    private boolean last = false;

    private int conditionIdCounter;
    private int runIdCounter;

    private StringMatchingPattern pattern;
    protected final List errors = new ArrayList(5);
    private final List conditions = new ArrayList(5);
    private final List runs = new ArrayList(2);
    protected final List setAttributes = new ArrayList(2);
    private boolean stopFilterChainOnMatch = false;

    private boolean toContainsVariable = false;
    private boolean toContainsBackReference = false;

    public static final String MATCH_TYPE_WILDCARD = "wildcard";
    public static final String DEFAULT_MATCH_TYPE = "regex";

    private boolean filter = false;

    /**
     * Constructor.
     */
    public RuleBase() {
        super();
        // empty
    }

    /**
     * Will run the rule against the uri and perform action required will return false is not matched
     * otherwise true.
     *
     * @param url
     * @param hsRequest
     * @return String of the rewritten url or the same as the url passed in if no match was made
     */
    protected RuleExecutionOutput matchesBase(String url, final HttpServletRequest hsRequest,
                                              final HttpServletResponse hsResponse, RuleChain chain)
            throws IOException, ServletException, InvocationTargetException {
        // make sure all the conditions match
        if (log.isDebugEnabled()) {
            String displayName = getDisplayName();
            log.debug(displayName + " run called with " + url);
        }
        if (!initialised) {
            log.debug("not initialised, skipping");
            return null;
        }
        if (!valid) {
            log.debug("not valid, skipping");
            return null;
        }
        if (!enabled) {
            log.debug("not enabled, skipping");
            return null;
        }
        if ( url == null ) {
            log.debug("url is null (maybe because of a previous match), skipping");
            return null;
        }

        StringMatchingMatcher matcher = pattern.matcher(url);
        boolean performToReplacement = false;
        if (toEmpty || stopFilterChainOnMatch) {
            // to is empty this must be an attempt to "set" and/or "run"
            if (!matcher.find()) {
                if (log.isTraceEnabled()) {
                    log.trace("no match on \"from\" (to is empty)");
                }
                return null;
            }
        } else {
            if (!matcher.find()) {
                if (log.isTraceEnabled()) {
                    log.trace("no match on \"from\" for " + from + " and " + url);
                }
                return null;
            }
            if (!toEmpty) {
                performToReplacement = true;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("matched \"from\"");
        }

        int conditionsSize = conditions.size();
        ConditionMatch lastConditionMatch = null;
        if (conditionsSize > 0) {
            boolean processNextOr = false;
            boolean currentResult = true;
            for (int i = 0; i < conditionsSize; i++) {
                final Condition condition = (Condition) conditions.get(i);
                ConditionMatch conditionMatch = condition.getConditionMatch(hsRequest);
                if (conditionMatch != null) {
                    lastConditionMatch = conditionMatch;
                }
                boolean conditionMatches = conditionMatch != null;
                if (processNextOr) {
                    currentResult |= conditionMatches;
                } else {
                    // must be and
                    currentResult &= conditionMatches;
                }
                processNextOr = condition.isProcessNextOr();
            }
            if (!currentResult) {
                log.debug("conditions do not match");
                return null;
            } else {
                log.debug("conditions match");
            }
        }

        // set a req attrib in case people want to use it
        hsRequest.setAttribute("org.tuckey.web.filters.urlrewrite.RuleMatched", Boolean.TRUE);

        // make sure the setAttributes are handled
        int setAttributesSize = setAttributes.size();
        if (setAttributesSize > 0) {
            log.trace("setting sttributes");
            for (int i = 0; i < setAttributesSize; i++) {
                SetAttribute setAttribute = (SetAttribute) setAttributes.get(i);
                setAttribute.execute(lastConditionMatch, matcher, hsRequest, hsResponse);
            }
        }

        // make sure the runs are handled
        int runsSize = runs.size();
        RewriteMatch lastRunMatch = null;
        if (runsSize > 0) {
            log.trace("performing runs");
            for (int i = 0; i < runsSize; i++) {
                Run run = (Run) runs.get(i);
                lastRunMatch = run.execute(hsRequest, hsResponse, matcher, lastConditionMatch, chain);
            }
        }

        String replacedTo = null;
        if (performToReplacement) {
            // replace back ref eg, %3 items
            // replace vars eg, %{txt}
            replacedTo = to;
            if (replacedTo != null) {
                // do variable replacement
                if (toContainsVariable) {
                    replacedTo = VariableReplacer.replace(replacedTo, hsRequest);
                }
                // perform backref replacement
                if (toContainsBackReference) {
                    replacedTo = BackReferenceReplacer.replace(lastConditionMatch, replacedTo);
                }
            }

            // get existing eg, $1 items
            replacedTo = matcher.replaceAll(replacedTo);
        }

        RuleExecutionOutput ruleExecutionOutput = new RuleExecutionOutput(replacedTo, true, lastRunMatch);

        // check for empty to element (valid when only set's)
        if (toEmpty) {
            log.debug("'to' is empty, no rewrite, only 'set' and or 'run'");
            return null;
        }

        // when match found but need to stop filter chain
        if (stopFilterChainOnMatch) {
            ruleExecutionOutput.setStopFilterMatch(true);
            ruleExecutionOutput.setReplacedUrl(null);
        }
        // the rewritten URL is unchanged if there was no <to> element.
        return ruleExecutionOutput;
    }


    public String getDisplayName() {
        return null;
    }


    /**
     * Will initialise the rule.
     *
     * @return true on success
     */
    public boolean initialise(ServletContext context) {
        // check all the conditions
        initialised = true;
        boolean ok = true;
        for (int i = 0; i < conditions.size(); i++) {
            final Condition condition = (Condition) conditions.get(i);
            condition.setRule(this);
            if (!condition.initialise()) {
                ok = false;
            }
        }
        for (int i = 0; i < runs.size(); i++) {
            final Run run = (Run) runs.get(i);
            if (!run.initialise(context)) {
                ok = false;
            }
            if ( run.isFilter() ) {
                log.debug("rule is a filtering rule");
                filter = true;
            }
        }
        for (int i = 0; i < setAttributes.size(); i++) {
            final SetAttribute setAttribute = (SetAttribute) setAttributes.get(i);
            if (!setAttribute.initialise()) {
                ok = false;
            }
        }
        // make sure default set for matchType
        if (! isMatchTypeWildcard()) {
            matchType = DEFAULT_MATCH_TYPE;
        }

        // compile the from regexp
        if (StringUtils.isBlank(from)) {
            addError("from is not valid because it is blank");
        } else {
            try {
                if (isMatchTypeWildcard()) {
                    log.debug("rule match type is wildcard");
                    pattern = new WildcardPattern(from);

                } else {
                    // default is regexp
                    pattern = new RegexPattern(from, fromCaseSensitive);
                }

            } catch (StringMatchingPatternSyntaxException e) {
                addError("from (" + from + ") is an invalid expression - " + e.getMessage());
            }
        }
        // set the substitution
        if (StringUtils.isBlank(to) && setAttributes.size() == 0 && runs.size() == 0) {
            addError("to is not valid because it is blank");
        } else if ("null".equalsIgnoreCase(to)) {
            stopFilterChainOnMatch = true;
        } else if (StringUtils.isBlank(to)) {
            toEmpty = true;
        } else if (!StringUtils.isBlank(to)) {
            // check for back refs
            if (BackReferenceReplacer.containsBackRef(to)) {
                toContainsBackReference = true;
            }
            // look for vars
            if (VariableReplacer.containsVariable(to)) {
                toContainsVariable = true;
            }
        }

        if (ok) {
            log.debug("loaded rule " + getFullDisplayName());
        } else {
            log.debug("failed to load rule");
        }
        if (errors.size() > 0) {
            ok = false;
        }
        valid = ok;
        return ok;
    }

    public boolean isMatchTypeWildcard() {
        return MATCH_TYPE_WILDCARD.equalsIgnoreCase(matchType);
    }

    public boolean isToContainsBackReference() {
        return toContainsBackReference;
    }

    public boolean isToContainsVariable() {
        return toContainsVariable;
    }

    public String getFullDisplayName() {
        return null;
    }

    protected void addError(String s) {
        errors.add(s);
        log.error(s);
    }

    /**
     * Destroy the rule gracefully.
     */
    public void destroy() {
        for (int i = 0; i < runs.size(); i++) {
            final Run run = (Run) runs.get(i);
            run.destroy();
        }
    }

    /**
     * Will get the contents of the from element.
     *
     * @return the contents of the from element
     */
    public String getFrom() {
        return from;
    }

    /**
     * Will set from, usually called by Digester.
     *
     * @param from the url to match from
     */
    public void setFrom(final String from) {
        this.from = from;
    }

    /**
     * Will set the to, usually called by Digester.
     *
     * @param to url for redirecting/passing through to
     */
    public void setTo(final String to) {
        if (!StringUtils.isBlank(to)) {
            this.to = to;
        }
    }

    /**
     * Set to type. note, it will default to false.
     *
     * @param lastStr true or false
     */
    public void setToLast(final String lastStr) {
        last = "true".equalsIgnoreCase(lastStr);
    }

    /**
     * Is this rule last?.
     *
     * @return boolean
     */
    public boolean isLast() {
        return last;
    }

    /**
     * Get to.
     *
     * @return String
     */
    public String getTo() {
        return to;
    }

    /**
     * Will get the rule's id.
     *
     * @return int
     */
    public int getId() {
        return id;
    }


    /**
     * Will get the list of errors.
     *
     * @return the list of errors
     */
    public List getErrors() {
        return errors;
    }

    /**
     * Will add the condition to the List.
     *
     * @param condition The Condition object to add
     */
    public void addCondition(final Condition condition) {
        conditions.add(condition);
        condition.setId(conditionIdCounter++);
    }

    /**
     * Will add the run to the List.
     *
     * @param run The Run object to add
     */
    public void addRun(final Run run) {
        runs.add(run);
        run.setId(runIdCounter++);
    }

    /**
     * Will add the SetAttribute to the List.
     *
     * @param setAttribute The SetAttribute object to add
     */
    public void addSetAttribute(final SetAttribute setAttribute) {
        setAttributes.add(setAttribute);
    }

    public List getSetAttributes() {
        return setAttributes;
    }

    /**
     * Will get the List of conditions.
     *
     * @return the List of Condition objects
     */
    public List getConditions() {
        return conditions;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isFromCaseSensitive() {
        return fromCaseSensitive;
    }

    public void setFromCaseSensitive(boolean fromCaseSensitive) {
        this.fromCaseSensitive = fromCaseSensitive;
    }

    public List getRuns() {
        return runs;
    }

    public boolean isValid() {
        return valid;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        matchType = StringUtils.trimToNull(matchType);
        if (MATCH_TYPE_WILDCARD.equalsIgnoreCase(matchType)) {
            this.matchType = MATCH_TYPE_WILDCARD;
        } else {
            this.matchType = DEFAULT_MATCH_TYPE;
        }
    }

    public boolean isFilter() {
        return filter;
    }

}
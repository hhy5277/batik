/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.css.engine;

import java.io.IOException;
import java.io.StringReader;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.batik.css.engine.sac.CSSConditionFactory;
import org.apache.batik.css.engine.sac.CSSSelectorFactory;

import org.apache.batik.css.engine.value.ComputedValue;
import org.apache.batik.css.engine.value.InheritValue;
import org.apache.batik.css.engine.value.ShorthandManager;
import org.apache.batik.css.engine.value.Value;
import org.apache.batik.css.engine.value.ValueManager;

import org.apache.batik.css.parser.ExtendedParser;

import org.apache.batik.css.engine.sac.ExtendedSelector;

import org.apache.batik.util.CSSConstants;
import org.apache.batik.util.ParsedURL;

import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.DocumentHandler;
import org.w3c.css.sac.InputSource;
import org.w3c.css.sac.LexicalUnit;
import org.w3c.css.sac.SelectorList;
import org.w3c.css.sac.SACMediaList;

import org.w3c.dom.Document;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.events.MutationEvent;

/**
 * This is the base class for all the CSS engines.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public abstract class CSSEngine {

    /**
     * Returns the next stylable parent of the given element.
     */
    public static CSSStylableElement getParentCSSStylableElement(Element elt) {
        Element e = getParentElement(elt);
        while (e != null) {
            if (e instanceof CSSStylableElement) {
                return (CSSStylableElement)e;
            }
            e = getParentElement(e);
        }
        return null;
    }

    /**
     * Returns the next parent element of the given element, from the
     * CSS point of view.
     */
    public static Element getParentElement(Element elt) {
        Node n = elt.getParentNode();
        while (n != null) {
            n = getLogicalParentNode(n);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return (Element)n;
            }
            n = n.getParentNode();
        }
        return null;
    }

    /**
     * Returns the logical parent of a node, given its physical parent.
     */
    public static Node getLogicalParentNode(Node parent) {
        Node node = parent;
        if (node != null) {
            if (node instanceof CSSImportedElementRoot) {
                return ((CSSImportedElementRoot)node).getCSSParentElement();
            } else {
                return node;
            }
        }
        return null;
    }

    /**
     * Returns the imported child of the given node, if any.
     */
    public static Node getImportedChild(Node node) {
        if (node instanceof CSSImportNode) {
            CSSImportNode inode = (CSSImportNode)node;
            CSSImportedElementRoot r = inode.getCSSImportedElementRoot();
            if (r == null) {
                return null;
            }
            return r.getFirstChild();
        }
        return null;
    }

    /**
     * The CSS context.
     */
    protected CSSContext cssContext;
    
    /**
     * The associated document.
     */
    protected Document document;

    /**
     * The document URI.
     */
    protected URL documentURI;

    /**
     * The property/int mappings.
     */
    protected StringIntMap indexes;

    /**
     * The shorthand-property/int mappings.
     */
    protected StringIntMap shorthandIndexes;

    /**
     * The value managers.
     */
    protected ValueManager[] valueManagers;

    /**
     * The shorthand managers.
     */
    protected ShorthandManager[] shorthandManagers;

    /**
     * The CSS parser.
     */
    protected ExtendedParser parser;

    /**
     * The pseudo-element names.
     */
    protected String[] pseudoElementNames;

    /**
     * The font-size property index.
     */
    protected int fontSizeIndex = -1;

    /**
     * The line-height property index.
     */
    protected int lineHeightIndex = -1;

    /**
     * The color property index.
     */
    protected int colorIndex = -1;

    /**
     * The user-agent style-sheet.
     */
    protected StyleSheet userAgentStyleSheet;

    /**
     * The user style-sheet.
     */
    protected StyleSheet userStyleSheet;

    /**
     * The media to use to cascade properties.
     */
    protected SACMediaList media;

    /**
     * The DOM nodes which contains StyleSheets.
     */
    protected List styleSheetNodes;

    /**
     * The style attribute namespace URI.
     */
    protected String styleNamespaceURI;

    /**
     * The style attribute local name.
     */
    protected String styleLocalName;
    
    /**
     * The class attribute namespace URI.
     */
    protected String classNamespaceURI;

    /**
     * The class attribute local name.
     */
    protected String classLocalName;
    
    /**
     * The non CSS presentational hints.
     */
    protected Set nonCSSPresentationalHints;

    /**
     * The non CSS presentational hints namespace URI.
     */
    protected String nonCSSPresentationalHintsNamespaceURI;

    /**
     * The style declaration document handler.
     */
    protected StyleDeclarationDocumentHandler styleDeclarationDocumentHandler =
        new StyleDeclarationDocumentHandler();

    /**
     * The style declaration update handler.
     */
    protected StyleDeclarationUpdateHandler styleDeclarationUpdateHandler;

    /**
     * The style sheet document handler.
     */
    protected StyleSheetDocumentHandler styleSheetDocumentHandler =
        new StyleSheetDocumentHandler();

    /**
     * The style declaration document handler used to build a
     * StyleDeclaration object.
     */
    protected StyleDeclarationBuilder styleDeclarationBuilder =
        new StyleDeclarationBuilder();

    /**
     * The current element.
     */
    protected CSSStylableElement element;

    /**
     * The current base URI.
     */
    protected URL cssBaseURI;

    /**
     * The alternate stylesheet title.
     */
    protected String alternateStyleSheet;

    /**
     * The DOMAttrModified event listener.
     */
    protected EventListener domAttrModifiedListener;

    /**
     * The DOMNodeInserted event listener.
     */
    protected EventListener domNodeInsertedListener;

    /**
     * The DOMNodeRemoved event listener.
     */
    protected EventListener domNodeRemovedListener;

    /**
     * The DOMSubtreeModified event listener.
     */
    protected EventListener domSubtreeModifiedListener;

    /**
     * The DOMCharacterDataModified event listener.
     */
    protected EventListener domCharacterDataModifiedListener;

    /**
     * Whether a style sheet as been removed from the document.
     */
    protected boolean styleSheetRemoved;

    /**
     * The right sibling of the last removed node.
     */
    protected Node removedStylableElementSibling;

    /**
     * The listeners.
     */
    protected List listeners = Collections.synchronizedList(new LinkedList());

    /**
     * The attributes found in stylesheets selectors.
     */
    protected Set selectorAttributes;

    /**
     * Used to fire a change event for all the properties.
     */
    protected final int[] ALL_PROPERTIES;

    /**
     * The CSS condition factory.
     */
    protected CSSConditionFactory cssConditionFactory;

    /**
     * Creates a new CSSEngine.
     * @param doc The associated document.
     * @param uri The document URI.
     * @param p The CSS parser.
     * @param vm The property value managers.
     * @param sm The shorthand properties managers.
     * @param pe The pseudo-element names supported by the associated
     *           XML dialect. Must be null if no support for pseudo-
     *           elements is required.
     * @param sns The namespace URI of the style attribute.
     * @param sln The local name of the style attribute.
     * @param cns The namespace URI of the class attribute.
     * @param cln The local name of the class attribute.
     * @param hints Whether the CSS engine should support non CSS
     *              presentational hints.
     * @param hintsNS The hints namespace URI.
     * @param ctx The CSS context.
     */
    protected CSSEngine(Document doc,
                        URL uri,
                        ExtendedParser p,
                        ValueManager[] vm,
                        ShorthandManager[] sm,
                        String[] pe,
                        String sns,
                        String sln,
                        String cns,
                        String cln,
                        boolean hints,
                        String hintsNS,
                        CSSContext ctx) {
        document = doc;
        documentURI = uri;
        parser = p;
        pseudoElementNames = pe;
        styleNamespaceURI = sns;
        styleLocalName = sln;
        classNamespaceURI = cns;
        classLocalName = cln;
        cssContext = ctx;

        cssConditionFactory = new CSSConditionFactory(cns, cln, null, "id");

        int len = vm.length;
        indexes = new StringIntMap(len);
        valueManagers = vm;

        for (int i = len - 1; i >= 0; --i) {
            String pn = vm[i].getPropertyName();
            indexes.put(pn, i);
            if (fontSizeIndex == -1 &&
                pn.equals(CSSConstants.CSS_FONT_SIZE_PROPERTY)) {
                fontSizeIndex = i;
            }
            if (lineHeightIndex == -1 &&
                pn.equals(CSSConstants.CSS_LINE_HEIGHT_PROPERTY)) {
                lineHeightIndex = i;
            }
            if (colorIndex == -1 &&
                pn.equals(CSSConstants.CSS_COLOR_PROPERTY)) {
                colorIndex = i;
            }
        }

        len = sm.length;
        shorthandIndexes = new StringIntMap(len);
        shorthandManagers = sm;
        for (int i = len - 1; i >= 0; --i) {
            shorthandIndexes.put(sm[i].getPropertyName(), i);
        }

        if (hints) {
            len = vm.length;
            nonCSSPresentationalHints = new HashSet();
            nonCSSPresentationalHintsNamespaceURI = hintsNS;
            for (int i = len - 1; i >= 0; --i) {
                String pn = vm[i].getPropertyName();
                nonCSSPresentationalHints.add(pn);
            }
        }

        if (document instanceof EventTarget) {
            // Attach the mutation events listeners.
            EventTarget et = (EventTarget)document;
            domAttrModifiedListener = new DOMAttrModifiedListener();
            et.addEventListener("DOMAttrModified",
                                domAttrModifiedListener,
                                false);
            domNodeInsertedListener = new DOMNodeInsertedListener();
            et.addEventListener("DOMNodeInserted",
                                domNodeInsertedListener,
                                false);
            domNodeRemovedListener = new DOMNodeRemovedListener();
            et.addEventListener("DOMNodeRemoved",
                                domNodeRemovedListener,
                                false);
            domSubtreeModifiedListener = new DOMSubtreeModifiedListener();
            et.addEventListener("DOMSubtreeModified",
                                domSubtreeModifiedListener,
                                false);
            domCharacterDataModifiedListener =
                new DOMCharacterDataModifiedListener();
            et.addEventListener("DOMCharacterDataModified",
                                domCharacterDataModifiedListener,
                                false);
            styleDeclarationUpdateHandler =
                new StyleDeclarationUpdateHandler();
        }

        ALL_PROPERTIES = new int[getNumberOfProperties()];
        for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
            ALL_PROPERTIES[i] = i;
        }
    }

    /**
     * Disposes the CSSEngine and all the attached resources.
     */
    public void dispose() {
        disposeStyleMaps(document.getDocumentElement());
        if (document instanceof EventTarget) {
            // Detach the mutation events listeners.
            EventTarget et = (EventTarget)document;
            et.removeEventListener("DOMAttrModified",
                                   domAttrModifiedListener,
                                   false);
            et.removeEventListener("DOMNodeInserted",
                                   domNodeInsertedListener,
                                   false);
            et.removeEventListener("DOMNodeRemoved",
                                   domNodeRemovedListener,
                                   false);
            et.removeEventListener("DOMSubtreeModified",
                                   domSubtreeModifiedListener,
                                   false);
            et.removeEventListener("DOMCharacterDataModified",
                                   domCharacterDataModifiedListener,
                                   false);
        }
    }

    private void disposeStyleMaps(Node node) {
        if (node instanceof CSSStylableElement) {
            ((CSSStylableElement)node).setComputedStyleMap(null, null);
        }
        for (Node n = node.getFirstChild();
             n != null;
             n = n.getNextSibling()) {
            if (n.getNodeType() == n.ELEMENT_NODE) {
                disposeStyleMaps(n);
            }
            Node c = getImportedChild(n);
            if (c != null) {
                disposeStyleMaps(c);
            }
        }
    }

    /**
     * Returns the CSS context.
     */
    public CSSContext getCSSContext() {
        return cssContext;
    }

    /**
     * Returns the document associated with this engine.
     */
    public Document getDocument() {
        return document;
    }

    /**
     * Returns the font-size property index.
     */
    public int getFontSizeIndex() {
        return fontSizeIndex;
    }

    /**
     * Returns the line-height property index.
     */
    public int getLineHeightIndex() {
        return lineHeightIndex;
    }

    /**
     * Returns the color property index.
     */
    public int getColorIndex() {
        return colorIndex;
    }

    /**
     * Returns the number of properties.
     */
    public int getNumberOfProperties() {
        return valueManagers.length;
    }

    /**
     * Returns the property index, or -1.
     */
    public int getPropertyIndex(String name) {
        return indexes.get(name);
    }

    /**
     * Returns the shorthand property index, or -1.
     */
    public int getShorthandIndex(String name) {
        return shorthandIndexes.get(name);
    }

    /**
     * Returns the name of the property at the given index.
     */
    public String getPropertyName(int idx) {
        return valueManagers[idx].getPropertyName();
    }

    /**
     * Sets the user agent style-sheet.
     */
    public void setUserAgentStyleSheet(StyleSheet ss) {
        userAgentStyleSheet = ss;
    }

    /**
     * Sets the user style-sheet.
     */
    public void setUserStyleSheet(StyleSheet ss) {
        userStyleSheet = ss;
    }

    /**
     * Returns the ValueManagers.
     */
    public ValueManager[] getValueManagers() {
        return valueManagers;
    }

    /**
     * Sets the media to use to compute the styles.
     */
    public void setMedia(String str) {
        try {
            media = parser.parseMedia(str);
        } catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("media.error",
                                       new Object[] { str,
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
    }

    /**
     * Sets the alternate style-sheet title.
     */
    public void setAlternateStyleSheet(String str) {
        alternateStyleSheet = str;
    }

    /**
     * Recursively imports the cascaded style from a source element
     * to an element of the current document.
     */
    public void importCascadedStyleMaps(Element src,
                                        CSSEngine srceng,
                                        Element dest) {
        if (src instanceof CSSStylableElement) {
            CSSStylableElement csrc  = (CSSStylableElement)src;
            CSSStylableElement cdest = (CSSStylableElement)dest;

            StyleMap sm = srceng.getCascadedStyleMap(csrc, null);
            sm.setFixedCascadedStyle(true);
            cdest.setComputedStyleMap(null, sm);

            if (pseudoElementNames != null) {
                int len = pseudoElementNames.length;
                for (int i = 0; i < len; i++) {
                    String pe = pseudoElementNames[i];
                    sm = srceng.getCascadedStyleMap(csrc, pe);
                    cdest.setComputedStyleMap(pe, sm);
                }
            }
        }

        for (Node dn = dest.getFirstChild(), sn = src.getFirstChild();
             dn != null;
             dn = dn.getNextSibling(), sn = sn.getNextSibling()) {
            if (sn.getNodeType() == Node.ELEMENT_NODE) {
                importCascadedStyleMaps((Element)sn, srceng, (Element)dn);
            }
        }
    }

    /**
     * Returns the current base-url.
     */
    public URL getCSSBaseURI() {
        if (cssBaseURI == null) {
            cssBaseURI = element.getCSSBase();
        }
        return cssBaseURI;
    }

    /**
     * Returns the cascaded style of the given element/pseudo-element.
     * @param elt The stylable element.
     * @param pseudo Optional pseudo-element string (null if none).
     */
    public StyleMap getCascadedStyleMap(CSSStylableElement elt,
                                        String pseudo) {
        int props = getNumberOfProperties();
        StyleMap result = new StyleMap(props);

        // Apply the user-agent style-sheet to the result.
        if (userAgentStyleSheet != null) {
            List rules = new ArrayList();
            addMatchingRules(rules, userAgentStyleSheet, elt, pseudo);
            addRules(elt, pseudo, result, rules, StyleMap.USER_AGENT_ORIGIN);
        }

        // Apply the user properties style-sheet to the result.
        if (userStyleSheet != null) {
            List rules = new ArrayList();
            addMatchingRules(rules, userStyleSheet, elt, pseudo);
            addRules(elt, pseudo, result, rules, StyleMap.USER_ORIGIN);
        }

        element = elt;

        // Apply the non-CSS presentational hints to the result.
        if (nonCSSPresentationalHints != null) {
            NamedNodeMap attrs = elt.getAttributes();
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
                Node attr = attrs.item(i);
                String an = attr.getNodeName();
                if (nonCSSPresentationalHints.contains(an)) {
                    try {
                        LexicalUnit lu;
                        int idx = getPropertyIndex(an);
                        lu = parser.parsePropertyValue(attr.getNodeValue());
                        ValueManager vm = valueManagers[idx];
                        Value v = vm.createValue(lu, this);
                        putAuthorProperty(result, idx, v, false,
                                          StyleMap.NON_CSS_ORIGIN);
                    } catch (Exception e) {
                        String m = e.getMessage();
                        String s =
                            Messages.formatMessage("property.syntax.error.at",
                                new Object[] { documentURI.toString(),
                                               an,
                                               attr.getNodeValue(),
                                               (m == null) ? "" : m });
                        throw new DOMException(DOMException.SYNTAX_ERR, s);
                    }
                }
            }
        }

        // Apply the document style-sheets to the result.
        List snodes = getStyleSheetNodes();
        int slen = snodes.size();
        if (slen > 0) {
            List rules = new ArrayList();
            for (int i = 0; i < slen; i++) {
                CSSStyleSheetNode ssn = (CSSStyleSheetNode)snodes.get(i);
                StyleSheet ss = ssn.getCSSStyleSheet();
                if (ss != null &&
                    (!ss.isAlternate() ||
                     ss.getTitle() == null ||
                     ss.getTitle().equals(alternateStyleSheet)) &&
                    mediaMatch(ss.getMedia())) {
                    addMatchingRules(rules, ss, elt, pseudo);
                }
            }
            addRules(elt, pseudo, result, rules, StyleMap.AUTHOR_ORIGIN);
        }

        // Apply the inline style to the result.
        if (styleLocalName != null) {
            String style = elt.getAttributeNS(styleNamespaceURI,
                                              styleLocalName);
            if (style.length() > 0) {
                try {
                    parser.setSelectorFactory(CSSSelectorFactory.INSTANCE);
                    parser.setConditionFactory(cssConditionFactory);
                    styleDeclarationDocumentHandler.styleMap = result;
                    parser.setDocumentHandler(styleDeclarationDocumentHandler);
                    parser.parseStyleDeclaration(style);
                    styleDeclarationDocumentHandler.styleMap = null;
                } catch (Exception e) {
                    String m = e.getMessage();
                    String s =
                        Messages.formatMessage("style.syntax.error.at",
                                      new Object[] { documentURI.toString(),
                                                     styleLocalName,
                                                     style,
                                                     (m == null) ? "" : m });
                    throw new DOMException(DOMException.SYNTAX_ERR, s);
                }
            }
        }
        
        element = null;
        cssBaseURI = null;

        return result;
    }

    /**
     * Returns the computed style of the given element/pseudo for the
     * property corresponding to the given index.
     */
    public Value getComputedStyle(CSSStylableElement elt,
                                  String pseudo,
                                  int propidx) {
        StyleMap sm = elt.getComputedStyleMap(pseudo);
        if (sm == null) {
            sm = getCascadedStyleMap(elt, pseudo);
            elt.setComputedStyleMap(pseudo, sm);
        }

        Value value = sm.getValue(propidx);
        if (!sm.isComputed(propidx)) {
            Value result = value;
            ValueManager vm = valueManagers[propidx];
            CSSStylableElement p = getParentCSSStylableElement(elt);
            if (value == null && (!vm.isInheritedProperty() || p == null)) {
                result = vm.getDefaultValue();
            } else if (value != null &&
                       (value == InheritValue.INSTANCE) &&
                       p != null) {
                result = null;
            }
            if (result == null) {
                // Value is 'inherit' and p != null.
                // The pseudo class is not propagated.
                result = getComputedStyle(p, null, propidx);
                sm.putParentRelative(propidx, true);
            } else {
                // Maybe is it a relative value.
                result = vm.computeValue(elt, pseudo, this, propidx,
                                         sm, result);
            }
            if (value == null) {
                sm.putValue(propidx, result);
                sm.putNullCascaded(propidx, true);
            } else if (result != value) {
                ComputedValue cv = new ComputedValue(value);
                cv.setComputedValue(result);
                sm.putValue(propidx, cv);
                result = cv;
            }
            sm.putComputed(propidx, true);
            value = result;
        }
        return value;
    }

    /**
     * Returns the document CSSStyleSheetNodes in a list. This list is
     * updated as the document is modified.
     */
    public List getStyleSheetNodes() {
        if (styleSheetNodes == null) {
            styleSheetNodes = new ArrayList();
            selectorAttributes = new HashSet();
            // Find all the style-sheets in the document.
            findStyleSheetNodes(document);
            int len = styleSheetNodes.size();
            for (int i = 0; i < len; i++) {
                CSSStyleSheetNode ssn;
                ssn = (CSSStyleSheetNode)styleSheetNodes.get(i);
                StyleSheet ss = ssn.getCSSStyleSheet();
                if (ss != null) {
                    findSelectorAttributes(selectorAttributes, ss);
                }
            }
        }
        return styleSheetNodes;
    }

    /**
     * An auxiliary method for getStyleSheets().
     */
    protected void findStyleSheetNodes(Node n) {
        if (n instanceof CSSStyleSheetNode) {
            styleSheetNodes.add(n);
        }
        for (Node nd = n.getFirstChild();
             nd != null;
             nd = nd.getNextSibling()) {
            findStyleSheetNodes(nd);
        }
    }

    /**
     * Finds the selector attributes in the given stylesheet.
     */
    protected void findSelectorAttributes(Set attrs, StyleSheet ss) {
        int len = ss.getSize();
        for (int i = 0; i < len; i++) {
            Rule r = ss.getRule(i);
            switch (r.getType()) {
            case StyleRule.TYPE:
                StyleRule style = (StyleRule)r;
                SelectorList sl = style.getSelectorList();
                int slen = sl.getLength();
                for (int j = 0; j < slen; j++) {
                    ExtendedSelector s = (ExtendedSelector)sl.item(j);
                    s.fillAttributeSet(attrs);
                }
                break;

            case MediaRule.TYPE:
            case ImportRule.TYPE:
                MediaRule mr = (MediaRule)r;
                if (mediaMatch(mr.getMediaList())) {
                    findSelectorAttributes(attrs, mr);
                }
                break;
            }
        }
    }

    /**
     * Parses and creates a property value.
     * @param prop The property name.
     * @param value The property value.
     */
    public Value parsePropertyValue(String prop, String value) {
        try {
            LexicalUnit lu;
            int idx = getPropertyIndex(prop);
            lu = parser.parsePropertyValue(value);
            ValueManager vm = valueManagers[idx];
            return vm.createValue(lu, this);
        } catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("property.syntax.error.at",
                                       new Object[] { documentURI.toString(),
                                                      prop,
                                                      value,
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
    }

    /**
     * Parses and creates a style declaration.
     * @param value The style declaration text.
     */
    public StyleDeclaration parseStyleDeclaration(String value) {
        try {
            parser.setSelectorFactory(CSSSelectorFactory.INSTANCE);
            parser.setConditionFactory(cssConditionFactory);
            cssBaseURI = documentURI;
            styleDeclarationBuilder.styleDeclaration = new StyleDeclaration();
            parser.setDocumentHandler(styleDeclarationBuilder);
            parser.parseStyleDeclaration(value);
            cssBaseURI = null;
            return styleDeclarationBuilder.styleDeclaration;
        } catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("syntax.error.at",
                                       new Object[] { documentURI.toString(),
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
    }

    /**
     * Parses and creates a new style-sheet.
     * @param uri The style-sheet URI.
     * @param media The target media of the style-sheet.
     */
    public StyleSheet parseStyleSheet(URL uri, String media)
        throws DOMException {
        StyleSheet ss = new StyleSheet();
        try {
            ss.setMedia(parser.parseMedia(media));
        } catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("syntax.error.at",
                                       new Object[] { documentURI.toString(),
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
        parseStyleSheet(ss, uri);
        return ss;
    }

    /**
     * Parses and creates a new style-sheet.
     * @param is The input source used to read the document.
     * @param uri The base URI.
     * @param media The target media of the style-sheet.
     */
    public StyleSheet parseStyleSheet(InputSource is, URL uri, String media)
        throws DOMException {
        StyleSheet ss = new StyleSheet();
        try {
            ss.setMedia(parser.parseMedia(media));
            parseStyleSheet(ss, is, uri);
        } catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("syntax.error.at",
                                       new Object[] { documentURI.toString(),
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
        return ss;
    }

    /**
     * Parses and fills the given style-sheet.
     * @param ss The stylesheet to fill.
     * @param uri The base URI.
     */
    public void parseStyleSheet(StyleSheet ss, URL uri) throws DOMException {
        if (uri == null) {
            String s = Messages.formatMessage("syntax.error.at",
                                              new Object[] { "Null Document reference", 
                                                             "" });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }

	try {
            // Check that access to the uri is allowed
             ParsedURL pDocURL = null;
             if (documentURI != null) {
                 pDocURL = new ParsedURL(documentURI);
             }
             ParsedURL pURL = null;
                 pURL = new ParsedURL(uri);
             cssContext.checkLoadExternalResource(pURL, pDocURL);
             
             parseStyleSheet(ss, new InputSource(uri.toString()), uri);
	} catch (SecurityException e) {
            throw e; 
        } catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("syntax.error.at",
                                       new Object[] { uri.toString(),
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
    }

    /**
     * Parses and creates a new style-sheet.
     * @param rules The style-sheet rules to parse.
     * @param uri The style-sheet URI.
     * @param media The target media of the style-sheet.
     */
    public StyleSheet parseStyleSheet(String rules, URL uri, String media)
        throws DOMException {
        StyleSheet ss = new StyleSheet();
        try {
            ss.setMedia(parser.parseMedia(media));
        } catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("syntax.error.at",
                                       new Object[] { documentURI.toString(),
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
        parseStyleSheet(ss, rules, uri);
        return ss;
    }

    /**
     * Parses and fills the given style-sheet.
     * @param ss The stylesheet to fill.
     * @param rules The style-sheet rules to parse.
     * @param uri The base URI.
     */
    public void parseStyleSheet(StyleSheet ss,
                                String rules,
                                URL uri) throws DOMException {
        try {
            parseStyleSheet(ss, new InputSource(new StringReader(rules)), uri);
	} catch (Exception e) {
            String m = e.getMessage();
            String s =
                Messages.formatMessage("stylesheet.syntax.error",
                                       new Object[] { uri.toString(),
                                                      rules,
                                                      (m == null) ? "" : m });
            throw new DOMException(DOMException.SYNTAX_ERR, s);
        }
    }

    /**
     * Parses and fills the given style-sheet.
     * @param ss The stylesheet to fill.
     * @param uri The base URI.
     */
    protected void parseStyleSheet(StyleSheet ss, InputSource is, URL uri)
        throws IOException {
        parser.setSelectorFactory(CSSSelectorFactory.INSTANCE);
        parser.setConditionFactory(cssConditionFactory);
        cssBaseURI = uri;
        styleSheetDocumentHandler.styleSheet = ss;
        parser.setDocumentHandler(styleSheetDocumentHandler);
        parser.parseStyleSheet(is);
        cssBaseURI = null;

        // Load the imported sheets.
        int len = ss.getSize();
        for (int i = 0; i < len; i++) {
            Rule r = (Rule)ss.getRule(i);
            if (r.getType() != ImportRule.TYPE) {
                // @import rules must be the first rules.
                break;
            }
            ImportRule ir = (ImportRule)r;
            parseStyleSheet(ir, ir.getURI());
        }
    }

    /**
     * Puts an author property from a style-map in another style-map,
     * if possible.
     */
    protected void putAuthorProperty(StyleMap dest,
                                     int idx,
                                     Value sval,
                                     boolean imp,
                                     short origin) {
        Value   dval = dest.getValue(idx);
        short   dorg = dest.getOrigin(idx);
        boolean dimp = dest.isImportant(idx);

        boolean cond = dval == null;
        if (!cond) {
            switch (dorg) {
            case StyleMap.USER_ORIGIN:
                cond = !dimp;
                break;
            case StyleMap.AUTHOR_ORIGIN:
                cond = !dimp || imp;
                break;
            default:
                cond = true;
            }
        }

        if (cond) {
            dest.putValue(idx, sval);
            dest.putImportant(idx, imp);
            dest.putOrigin(idx, origin);
        }
    }

    /**
     * Adds the rules matching the element/pseudo-element of given style
     * sheet to the list.
     */
    protected void addMatchingRules(List rules,
                                    StyleSheet ss,
                                    Element elt,
                                    String pseudo) {
        int len = ss.getSize();
        for (int i = 0; i < len; i++) {
            Rule r = ss.getRule(i);
            switch (r.getType()) {
            case StyleRule.TYPE:
                StyleRule style = (StyleRule)r;
                SelectorList sl = style.getSelectorList();
                int slen = sl.getLength();
                for (int j = 0; j < slen; j++) {
                    ExtendedSelector s = (ExtendedSelector)sl.item(j);
                    if (s.match(elt, pseudo)) {
                        rules.add(style);
                    }
                }
                break;

            case MediaRule.TYPE:
            case ImportRule.TYPE:
                MediaRule mr = (MediaRule)r;
                if (mediaMatch(mr.getMediaList())) {
                    addMatchingRules(rules, mr, elt, pseudo);
                }
                break;
            }
        }
    }

    /**
     * Adds the rules contained in the given list to a stylemap.
     */
    protected void addRules(Element elt,
                            String pseudo,
                            StyleMap sm,
                            List rules,
                            short origin) {
        sortRules(rules, elt, pseudo);
        int rlen = rules.size();
        int props = getNumberOfProperties();

        if (origin == StyleMap.AUTHOR_ORIGIN) {
            for (int r = 0; r < rlen; r++) {
                StyleRule sr = (StyleRule)rules.get(r);
                StyleDeclaration sd = sr.getStyleDeclaration();
                int len = sd.size();
                for (int i = 0; i < len; i++) {
                    putAuthorProperty(sm,
                                      sd.getIndex(i),
                                      sd.getValue(i),
                                      sd.getPriority(i),
                                      origin);
                }
            }
        } else {
            for (int r = 0; r < rlen; r++) {
                StyleRule sr = (StyleRule)rules.get(r);
                StyleDeclaration sd = sr.getStyleDeclaration();
                int len = sd.size();
                for (int i = 0; i < len; i++) {
                    int idx = sd.getIndex(i);
                    sm.putValue(idx, sd.getValue(i));
                    sm.putImportant(idx, sd.getPriority(i));
                    sm.putOrigin(idx, origin);
                }
            }
        }
    }

    /**
     * Sorts the rules matching the element/pseudo-element of given style
     * sheet to the list.
     */
    protected void sortRules(List rules, Element elt, String pseudo) {
        int len = rules.size();
        for (int i = 0; i < len - 1; i++) {
            int idx = i;
            int min = Integer.MAX_VALUE;
            for (int j = i; j < len; j++) {
                StyleRule r = (StyleRule)rules.get(j);
                SelectorList sl = r.getSelectorList();
                int spec = 0;
                int slen = sl.getLength();
                for (int k = 0; k < slen; k++) {
                    ExtendedSelector s = (ExtendedSelector)sl.item(k);
                    if (s.match(elt, pseudo)) {
                        int sp = s.getSpecificity();
                        if (sp > spec) {
                            spec = sp;
                        }
                    }
                }
                if (spec < min) {
                    min = spec;
                    idx = j;
                }
            }
            if (i != idx) {
                Object tmp = rules.get(i);
                rules.set(i, rules.get(idx));
                rules.set(idx, tmp);
            }
        }
    }

    /**
     * Whether the given media list matches the media list of this
     * CSSEngine object.
     */
    protected boolean mediaMatch(SACMediaList ml) {
	if (media == null ||
            ml == null ||
            media.getLength() == 0 ||
            ml.getLength() == 0) {
	    return true;
	}
	for (int i = 0; i < ml.getLength(); i++) {
	    for (int j = 0; j < media.getLength(); j++) {
		if (media.item(j).equalsIgnoreCase("all") ||
                    ml.item(i).equalsIgnoreCase(media.item(j))) {
		    return true;
		}
	    }
	}
	return false;
    }

    /**
     * To parse a style declaration.
     */
    protected class StyleDeclarationDocumentHandler
        extends DocumentAdapter
        implements ShorthandManager.PropertyHandler {
        public StyleMap styleMap;
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#property(String,LexicalUnit,boolean)}.
         */
        public void property(String name, LexicalUnit value, boolean important)
            throws CSSException {
            int i = getPropertyIndex(name);
            if (i == -1) {
                i = getShorthandIndex(name);
                if (i == -1) {
                    // Unknown property
                    return;
                }
                shorthandManagers[i].setValues(CSSEngine.this,
                                               this,
                                               value,
                                               important);
            } else {
                Value v = valueManagers[i].createValue(value, CSSEngine.this);
                putAuthorProperty(styleMap, i, v, important,
                                  StyleMap.INLINE_AUTHOR_ORIGIN);
            }
        }
    }

    /**
     * To build a StyleDeclaration object.
     */
    protected class StyleDeclarationBuilder
        extends DocumentAdapter
        implements ShorthandManager.PropertyHandler {
        public StyleDeclaration styleDeclaration;
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#property(String,LexicalUnit,boolean)}.
         */
        public void property(String name, LexicalUnit value, boolean important)
            throws CSSException {
            int i = getPropertyIndex(name);
            if (i == -1) {
                i = getShorthandIndex(name);
                if (i == -1) {
                    // Unknown property
                    return;
                }
                shorthandManagers[i].setValues(CSSEngine.this,
                                               this,
                                               value,
                                               important);
            } else {
                Value v = valueManagers[i].createValue(value, CSSEngine.this);
                styleDeclaration.append(v, i, important);
            }
        }
    }

    /**
     * To parse a style sheet.
     */
    protected class StyleSheetDocumentHandler
        extends DocumentAdapter
        implements ShorthandManager.PropertyHandler {
        public StyleSheet styleSheet;
        protected StyleRule styleRule;
        protected StyleDeclaration styleDeclaration;

        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#startDocument(InputSource)}.
         */
        public void startDocument(InputSource source)
            throws CSSException {
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#endDocument(InputSource)}.
         */
        public void endDocument(InputSource source) throws CSSException {
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#ignorableAtRule(String)}.
         */
        public void ignorableAtRule(String atRule) throws CSSException {
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#importStyle(String,SACMediaList,String)}.
         */
        public void importStyle(String       uri,
                                SACMediaList media, 
                                String       defaultNamespaceURI)
            throws CSSException {
            ImportRule ir = new ImportRule();
            ir.setMediaList(media);
            ir.setParent(styleSheet);
            try {
                ir.setURI(new URL(getCSSBaseURI(), uri));
            } catch (MalformedURLException e) {
            }
            styleSheet.append(ir);
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#startMedia(SACMediaList)}.
         */
        public void startMedia(SACMediaList media) throws CSSException {
            MediaRule mr = new MediaRule();
            mr.setMediaList(media);
            mr.setParent(styleSheet);
            styleSheet.append(mr);
            styleSheet = mr;
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#endMedia(SACMediaList)}.
         */
        public void endMedia(SACMediaList media) throws CSSException {
            styleSheet = styleSheet.getParent();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#startPage(String,String)}.
         */    
        public void startPage(String name, String pseudo_page)
            throws CSSException {
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#endPage(String,String)}.
         */
        public void endPage(String name, String pseudo_page)
            throws CSSException {
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#startFontFace()}.
         */
        public void startFontFace() throws CSSException {
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#endFontFace()}.
         */
        public void endFontFace() throws CSSException {
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#startSelector(SelectorList)}.
         */
        public void startSelector(SelectorList selectors) throws CSSException {
            styleRule = new StyleRule();
            styleRule.setSelectorList(selectors);
            styleDeclaration = new StyleDeclaration();
            styleRule.setStyleDeclaration(styleDeclaration);
            styleSheet.append(styleRule);
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * org.w3c.css.sac.DocumentHandler#endSelector(SelectorList)}.
         */
        public void endSelector(SelectorList selectors) throws CSSException {
            styleRule = null;
            styleDeclaration = null;
        }

        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#property(String,LexicalUnit,boolean)}.
         */
        public void property(String name, LexicalUnit value, boolean important)
            throws CSSException {
            int i = getPropertyIndex(name);
            if (i == -1) {
                i = getShorthandIndex(name);
                if (i == -1) {
                    // Unknown property
                    return;
                }
                shorthandManagers[i].setValues(CSSEngine.this,
                                               this,
                                               value,
                                               important);
            } else {
                Value v = valueManagers[i].createValue(value, CSSEngine.this);
                styleDeclaration.append(v, i, important);
            }
        }
    }

    /**
     * Provides an adapter for the DocumentHandler interface.
     */
    protected static class DocumentAdapter implements DocumentHandler {

        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#startDocument(InputSource)}.
         */
        public void startDocument(InputSource source)
            throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#endDocument(InputSource)}.
         */
        public void endDocument(InputSource source) throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#comment(String)}.
         */
        public void comment(String text) throws CSSException {
            // We always ignore the comments.
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#ignorableAtRule(String)}.
         */
        public void ignorableAtRule(String atRule) throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#namespaceDeclaration(String,String)}.
         */
        public void namespaceDeclaration(String prefix, String uri) 
            throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#importStyle(String,SACMediaList,String)}.
         */
        public void importStyle(String       uri,
                                SACMediaList media, 
                                String       defaultNamespaceURI)
            throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#startMedia(SACMediaList)}.
         */
        public void startMedia(SACMediaList media) throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#endMedia(SACMediaList)}.
         */
        public void endMedia(SACMediaList media) throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#startPage(String,String)}.
         */    
        public void startPage(String name, String pseudo_page)
            throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#endPage(String,String)}.
         */
        public void endPage(String name, String pseudo_page)
            throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link DocumentHandler#startFontFace()}.
         */
        public void startFontFace() throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link DocumentHandler#endFontFace()}.
         */
        public void endFontFace() throws CSSException {
            throw new InternalError();
        }
        
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#startSelector(SelectorList)}.
         */
        public void startSelector(SelectorList selectors) throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#endSelector(SelectorList)}.
         */
        public void endSelector(SelectorList selectors) throws CSSException {
            throw new InternalError();
        }
    
        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#property(String,LexicalUnit,boolean)}.
         */
        public void property(String name, LexicalUnit value, boolean important)
            throws CSSException {
            throw new InternalError();
        }
    }

    // CSS events /////////////////////////////////////////////////////////
    
    protected final static CSSEngineListener[] LISTENER_ARRAY =
        new CSSEngineListener[0];

    /**
     * Adds a CSS engine listener.
     */
    public void addCSSEngineListener(CSSEngineListener l) {
        listeners.add(l);
    }

    /**
     * Removes a CSS engine listener.
     */
    public void removeCSSEngineListener(CSSEngineListener l) {
        listeners.remove(l);
    }

    /**
     * Fires a CSSEngineEvent, given a list of modified properties.
     */
    protected void firePropertiesChangedEvent(Element target, int[] props) {
        CSSEngineListener[] ll =
            (CSSEngineListener[])listeners.toArray(LISTENER_ARRAY);

        int len = ll.length;
        if (len > 0) {
            CSSEngineEvent evt = new CSSEngineEvent(this, target, props);
            for (int i = 0; i < len; i++) {
                ll[i].propertiesChanged(evt);
            }
        }
    }

    // Dynamic updates ////////////////////////////////////////////////////
    
    /**
     * Called when the inline style of the given element has been updated.
     */
    protected void inlineStyleAttributeUpdated(CSSStylableElement elt,
                                               StyleMap style,
                                               MutationEvent evt) {
        boolean[] updated = styleDeclarationUpdateHandler.updatedProperties;
        for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
            updated[i] = false;
        }

        switch (evt.getAttrChange()) {
        case MutationEvent.ADDITION:
        case MutationEvent.MODIFICATION:
            String decl = evt.getNewValue();
            if (decl.length() > 0) {
                element = elt;
                try {
                    parser.setSelectorFactory(CSSSelectorFactory.INSTANCE);
                    parser.setConditionFactory(cssConditionFactory);
                    styleDeclarationUpdateHandler.styleMap = style;
                    parser.setDocumentHandler(styleDeclarationUpdateHandler);
                    parser.parseStyleDeclaration(decl);
                    styleDeclarationUpdateHandler.styleMap = null;
                } catch (Exception e) {
                    String m = e.getMessage();
                    String s =
                        Messages.formatMessage("style.syntax.error.at",
                                      new Object[] { documentURI.toString(),
                                                     styleLocalName,
                                                     decl,
                                                     (m == null) ? "" : m });
                    throw new DOMException(DOMException.SYNTAX_ERR, s);
                }
                element = null;
                cssBaseURI = null;
            }

            // Fall through

        case MutationEvent.REMOVAL:
            boolean removed = false;

            if (evt.getPrevValue() != null &&
                evt.getPrevValue().length() > 0) {
                // Check if the style map has cascaded styles which
                // come from the inline style attribute.
                for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
                    if (style.isComputed(i) &&
                        style.getOrigin(i) == StyleMap.INLINE_AUTHOR_ORIGIN &&
                        !updated[i]) {
                        removed = true;
                        updated[i] = true;
                    }
                }
            }

            if (removed) {
                // Invalidate all the values.
                elt.setComputedStyleMap(null, null);

                firePropertiesChangedEvent(elt, ALL_PROPERTIES);
                    
                Node c = getImportedChild(elt);
                if (c != null) {
                    propagateChanges(c, ALL_PROPERTIES);
                }
                for (Node n = elt.getFirstChild();
                     n != null;
                     n = n.getNextSibling()) {
                    propagateChanges(n, ALL_PROPERTIES);
                }
            } else {
                int count = 0;
            

                // Invalidate the relative values
                boolean fs = (fontSizeIndex == -1)
                    ? false
                    : updated[fontSizeIndex];
                boolean lh = (lineHeightIndex == -1)
                    ? false
                    : updated[lineHeightIndex];
                boolean cl = (colorIndex == -1)
                    ? false
                    : updated[colorIndex];
                
                for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
                    if (!updated[i]) {
                        if (style.isComputed(i)) {
                            if (fs && style.isFontSizeRelative(i)) {
                                updated[i] = true;
                                count++;
                                clearComputedValue(style, i);
                            }
                            if (lh && style.isLineHeightRelative(i)) {
                                updated[i] = true;
                                count++;
                                clearComputedValue(style, i);
                            }
                            if (cl && style.isColorRelative(i)) {
                                updated[i] = true;
                                count++;
                                clearComputedValue(style, i);
                            }
                        }
                    } else {
                        count++;
                    }
                }

                if (count > 0) {
                    int[] props = new int[count];
                    count = 0;
                    for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
                        if (updated[i]) {
                            props[count++] = i;
                        }
                    }
                    firePropertiesChangedEvent(elt, props);
                    
                    Node c = getImportedChild(elt);
                    if (c != null) {
                        propagateChanges(c, props);
                    }
                    for (Node n = elt.getFirstChild();
                         n != null;
                         n = n.getNextSibling()) {
                        propagateChanges(n, props);
                    }
                }
            }
            break;

        default:
            // Must not happen
            throw new InternalError("Invalid attrChangeType");
        }
    }

    private static void clearComputedValue(StyleMap style, int n) {
        if (style.isNullCascaded(n)) {
            style.putValue(n, null);
        } else {
            Value v = style.getValue(n);
            if (v instanceof ComputedValue) {
                ComputedValue cv = (ComputedValue)v;
                v = cv.getCascadedValue();
                style.putValue(n, v);
            }
        }
        style.putComputed(n, false);
    }

    /**
     * Invalidates all the stylable elements descendant of the given
     * node, and the node.
     */
    protected void invalidateTreeProperties(Node node) {
        if (node instanceof CSSStylableElement) {
            CSSStylableElement elt = (CSSStylableElement)node;
            StyleMap style = elt.getComputedStyleMap(null);
            if (style != null) {
                elt.setComputedStyleMap(null, null);
                firePropertiesChangedEvent(elt, ALL_PROPERTIES);
            }
        }

        Node c = getImportedChild(node);
        if (c != null) {
            propagateChanges(c, ALL_PROPERTIES);
        }
        for (Node n = node.getFirstChild();
             n != null;
             n = n.getNextSibling()) {
            invalidateTreeProperties(n);
        }
    }

    /**
     * Invalidates all the properties of the given node.
     */
    protected void invalidateProperties(Node node) {
        if (node instanceof CSSStylableElement) {
            CSSStylableElement elt = (CSSStylableElement)node;
            StyleMap style = elt.getComputedStyleMap(null);
            if (style != null) {
                elt.setComputedStyleMap(null, null);
                firePropertiesChangedEvent(elt, ALL_PROPERTIES);
            }
        }

        Node c = getImportedChild(node);
        if (c != null) {
            propagateChanges(c, ALL_PROPERTIES);
        }
        for (Node n = node.getFirstChild();
             n != null;
             n = n.getNextSibling()) {
            propagateChanges(n, ALL_PROPERTIES);
        }
    }

    /**
     * Propagates the changes that occurs on the parent of the given node.
     */
    protected void propagateChanges(Node node, int[] props) {
        if (node instanceof CSSStylableElement) {
            CSSStylableElement elt = (CSSStylableElement)node;
            StyleMap style = elt.getComputedStyleMap(null);
            if (style != null) {
                boolean[] updated =
                    styleDeclarationUpdateHandler.updatedProperties;
                for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
                    updated[i] = false;
                }
                for (int i = props.length - 1; i >= 0; --i) {
                    int idx = props[i];
                    if (style.isComputed(idx) &&
                        style.isParentRelative(idx)) {
                        updated[idx] = true;
                        clearComputedValue(style, idx);
                    }
                }

                // Invalidate the relative values
                boolean fs = (fontSizeIndex == -1)
                    ? false
                    : updated[fontSizeIndex];
                boolean lh = (lineHeightIndex == -1)
                    ? false
                    : updated[lineHeightIndex];
                boolean cl = (colorIndex == -1)
                    ? false
                    : updated[colorIndex];
                int count = 0;

                for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
                    if (!updated[i]) {
                        if (style.isComputed(i)) {
                            if (fs && style.isFontSizeRelative(i)) {
                                updated[i] = true;
                                count++;
                                clearComputedValue(style, i);
                            }
                            if (lh && style.isLineHeightRelative(i)) {
                                updated[i] = true;
                                count++;
                                clearComputedValue(style, i);
                            }
                            if (cl && style.isColorRelative(i)) {
                                updated[i] = true;
                                count++;
                                clearComputedValue(style, i);
                            }
                        }
                    } else {
                        count++;
                    }
                }

                if (count > 0) {
                    props = new int[count];
                    count = 0;
                    for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
                        if (updated[i]) {
                            props[count++] = i;
                        }
                    }
                    firePropertiesChangedEvent(elt, props);
                } else {
                    props = null;
                }
            }
        }
        if (props != null) {
            Node c = getImportedChild(node);
            if (c != null) {
                propagateChanges(c, props);
            }
            for (Node n = node.getFirstChild();
                 n != null;
                 n = n.getNextSibling()) {
                propagateChanges(n, props);
            }
        }
    }

    /**
     * To parse a style declaration and update a StyleMap.
     */
    protected class StyleDeclarationUpdateHandler
        extends DocumentAdapter
        implements ShorthandManager.PropertyHandler {
        public StyleMap styleMap;
        public boolean[] updatedProperties =
            new boolean[getNumberOfProperties()];

        /**
         * <b>SAC</b>: Implements {@link
         * DocumentHandler#property(String,LexicalUnit,boolean)}.
         */
        public void property(String name, LexicalUnit value, boolean important)
            throws CSSException {
            int i = getPropertyIndex(name);
            if (i == -1) {
                i = getShorthandIndex(name);
                if (i == -1) {
                    // Unknown property
                    return;
                }
                shorthandManagers[i].setValues(CSSEngine.this,
                                               this,
                                               value,
                                               important);
            } else {
                if (styleMap.isImportant(i)) {
                    // The previous value is important, and a value
                    // from a style attribute cannot be important...
                    return;
                }

                if (styleMap.isComputed(i)) {
                    updatedProperties[i] = true;
                }

                Value v = valueManagers[i].createValue(value, CSSEngine.this);
                styleMap.putMask(i, (short)0);
                styleMap.putValue(i, v);
                styleMap.putOrigin(i, StyleMap.INLINE_AUTHOR_ORIGIN);
            }
        }
    }

    /**
     * Called when a non-CSS presentational hint has been updated.
     */
    protected void nonCSSPresentationalHintUpdated(CSSStylableElement elt,
                                                   StyleMap style,
                                                   String property,
                                                   MutationEvent evt) {
        int idx = getPropertyIndex(property);

        if (style.isImportant(idx)) {
            // The current value is important, and a value
            // from an XML attribute cannot be important...
            return;
        }

        switch (style.getOrigin(idx)) {
        case StyleMap.AUTHOR_ORIGIN:
        case StyleMap.INLINE_AUTHOR_ORIGIN:
            // The current value has a greater priority
            return;
        }
        
        boolean comp = style.isComputed(idx);

        switch (evt.getAttrChange()) {
        case MutationEvent.ADDITION:
        case MutationEvent.MODIFICATION:
            element = elt;
            try {
                LexicalUnit lu;
                lu = parser.parsePropertyValue(evt.getNewValue());
                ValueManager vm = valueManagers[idx];
                Value v = vm.createValue(lu, CSSEngine.this);
                style.putMask(idx, (short)0);
                style.putValue(idx, v);
                style.putOrigin(idx, StyleMap.NON_CSS_ORIGIN);
            } catch (Exception e) {
                String m = e.getMessage();
                String s =
                    Messages.formatMessage("property.syntax.error.at",
                        new Object[]
                        { documentURI.toString(),
                          property,
                          evt.getNewValue(),
                          (m == null) ? "" : m });
                throw new DOMException(DOMException.SYNTAX_ERR, s);
            }
            element = null;
            cssBaseURI = null;
            break;

        case MutationEvent.REMOVAL:
            // Invalidate all the values.
            elt.setComputedStyleMap(null, null);
            
            firePropertiesChangedEvent(elt, ALL_PROPERTIES);
                    
            Node c = getImportedChild(elt);
            if (c != null) {
                propagateChanges(c, ALL_PROPERTIES);
            }
            for (Node n = elt.getFirstChild();
                 n != null;
                 n = n.getNextSibling()) {
                propagateChanges(n, ALL_PROPERTIES);
            }
            return;
        }

        if (!comp) {
            // The previous value was not computed: nobody is
            // interested by this property modifications
            return;
        }

        boolean[] updated = styleDeclarationUpdateHandler.updatedProperties;
        for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
            updated[i] = false;
        }
        updated[idx] = true;

        // Invalidate the relative values
        boolean fs = idx == fontSizeIndex;
        boolean lh = idx == lineHeightIndex;
        boolean cl = idx == colorIndex;
        int count = 0;

        for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
            if (!updated[i]) {
                if (style.isComputed(i)) {
                    if (fs && style.isFontSizeRelative(i)) {
                        updated[i] = true;
                        count++;
                        clearComputedValue(style, i);
                    }
                    if (lh && style.isLineHeightRelative(i)) {
                        updated[i] = true;
                        count++;
                        clearComputedValue(style, i);
                    }
                    if (cl && style.isColorRelative(i)) {
                        updated[i] = true;
                        count++;
                        clearComputedValue(style, i);
                    }
                }
            } else {
                count++;
            }
        }

        int[] props = new int[count];
        count = 0;
        for (int i = getNumberOfProperties() - 1; i >= 0; --i) {
            if (updated[i]) {
                props[count++] = i;
            }
        }
        firePropertiesChangedEvent(elt, props);

        Node c = getImportedChild(elt);
        if (c != null) {
            propagateChanges(c, props);
        }
        for (Node n = elt.getFirstChild();
             n != null;
             n = n.getNextSibling()) {
            propagateChanges(n, props);
        }
    }

    /**
     * To handle the insertion of a CSSStyleSheetNode in the
     * associated document.
     */
    protected class DOMNodeInsertedListener implements EventListener {
        public void handleEvent(Event evt) {
            EventTarget et = evt.getTarget();
            if (et instanceof CSSStyleSheetNode) {
                styleSheetNodes = null;

                // Invalidate all the CSSStylableElements in the document.
                invalidateTreeProperties(document.getDocumentElement());
                return;
            }
            if (et instanceof CSSStylableElement) {
                // Invalidate the CSSStylableElement siblings, to
                // correctly match the adjacent selectors and
                // first-child pseudo-class.
                for (Node n = ((Node)evt.getTarget()).getNextSibling();
                     n != null;
                     n = n.getNextSibling()) {
                    invalidateProperties(n);
                }
            }
        }
    }

    /**
     * To handle the removal of a CSSStyleSheetNode from the
     * associated document.
     */
    protected class DOMNodeRemovedListener implements EventListener {
        public void handleEvent(Event evt) {
            EventTarget et = evt.getTarget();
            if (et instanceof CSSStyleSheetNode) {
                // Wait for the DOMSubtreeModified to do the invalidations
                // because at this time the node is in the tree.
                styleSheetRemoved = true;
            } else if (et instanceof CSSStylableElement) {
                // Wait for the DOMSubtreeModified to do the invalidations
                // because at this time the node is in the tree.
                removedStylableElementSibling = ((Node)et).getNextSibling();
            }
            // Clears the computed styles in the removed tree.
            disposeStyleMaps((Node)et);
        }
    }

    /**
     * To handle the removal of a CSSStyleSheetNode from the
     * associated document.
     */
    protected class DOMSubtreeModifiedListener implements EventListener {
        public void handleEvent(Event evt) {
            if (styleSheetRemoved) {
                styleSheetRemoved = false;
                styleSheetNodes = null;

                // Invalidate all the CSSStylableElements in the document.
                invalidateTreeProperties(document.getDocumentElement());
            } else if (removedStylableElementSibling != null) {
                // Invalidate the CSSStylableElement siblings, to
                // correctly match the adjacent selectors and
                // first-child pseudo-class.
                for (Node n = removedStylableElementSibling;
                     n != null;
                     n = n.getNextSibling()) {
                    invalidateProperties(n);
                }
                removedStylableElementSibling = null;
            }
        }
    }

    /**
     * To handle the modification of a CSSStyleSheetNode.
     */
    protected class DOMCharacterDataModifiedListener implements EventListener {
        public void handleEvent(Event evt) {
            Node n = (Node)evt.getTarget();
            if (n.getParentNode() instanceof CSSStyleSheetNode) {
                styleSheetNodes = null;

                // Invalidate all the CSSStylableElements in the document.
                invalidateTreeProperties(document.getDocumentElement());
            }
        }
    }

    /**
     * To handle the element attributes modification in the associated
     * document.
     */
    protected class DOMAttrModifiedListener implements EventListener {
        public void handleEvent(Event evt) {
            EventTarget et = evt.getTarget();
            if (!(et instanceof CSSStylableElement)) {
                // Not a stylable element.
                return;
            }

            CSSStylableElement elt = (CSSStylableElement)et;
            StyleMap style = elt.getComputedStyleMap(null);
            if (style == null) {
                // Nobody ever asked for the computed style of the
                // element, so it does not require an update...
                return;
            }

            MutationEvent mevt = (MutationEvent)evt;
            Node attr = mevt.getRelatedNode();
            String attrNS = attr.getNamespaceURI();
            if ((attrNS == null && styleNamespaceURI == null) ||
                (attrNS != null && attrNS.equals(styleNamespaceURI))) {
                String name = (attrNS == null)
                    ? attr.getNodeName()
                    : attr.getLocalName();
                if (name.equals(styleLocalName)) {
                    // The style declaration attribute has been modified.

                    inlineStyleAttributeUpdated(elt, style, mevt);

                    return;
                }
            }

            String name = (attrNS == null)
                ? attr.getNodeName()
                : attr.getLocalName();
                    
            if (nonCSSPresentationalHints != null) {
                if ((attrNS == null &&
                     nonCSSPresentationalHintsNamespaceURI == null) ||
                    (attrNS != null &&
                     attrNS.equals(nonCSSPresentationalHintsNamespaceURI))) {
                    if (nonCSSPresentationalHints.contains(name)) {
                        // The 'name' attribute which represents a non CSS
                        // presentational hint has been modified.

                        nonCSSPresentationalHintUpdated(elt, style, name,
                                                        mevt);
                        return;
                    }
                }
            }

            if (selectorAttributes != null &&
                selectorAttributes.contains(name)) {
                // An attribute has been modified, invalidate all the
                // properties to correctly match attribute selectors.

                elt.setComputedStyleMap(null, null);

                firePropertiesChangedEvent(elt, ALL_PROPERTIES);
                
                Node c = getImportedChild(elt);
                if (c != null) {
                    propagateChanges(c, ALL_PROPERTIES);
                }
                for (Node n = elt.getFirstChild();
                     n != null;
                     n = n.getNextSibling()) {
                    propagateChanges(n, ALL_PROPERTIES);
                }
            }
        }
    }
}

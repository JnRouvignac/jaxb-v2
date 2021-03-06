/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.tools.xjc.reader.xmlschema.parser;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import javax.xml.namespace.QName;

import com.sun.tools.xjc.reader.Const;
import com.sun.xml.bind.v2.WellKnownNamespace;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Checks if binding declarations are placed where they are allowed.
 * 
 * <p>
 * For example, if a {@code <jaxb:property>} customization is given under
 * the {@code <xs:simpleContent>} element, this class raises an error.
 * 
 * <p>
 * our main checkpoint of misplaced customizations are in BGMBuilder.
 * There, we mark a customization whenever we use it. At the end of the
 * day, we look for unmarked customizations and raise errors for them.
 * 
 * <p>
 * Between this approach and the JAXB spec 1.0 is a problem that
 * the spec allows/prohibits customizations at schema element level,
 * while BGMBuilder and XSOM works on schema component levels.
 * 
 * <p>
 * For example, a property customization is allowed on a complex type
 * schema component, but it's only allowed on the {@code <complexType>}
 * element. The spec team informed us that they would consider resolving
 * this discrepancy in favor of RI, but meanwhile we need to detect
 * errors correctly.
 * 
 * <p>
 * This filter is implemented for this purpose.
 * 
 * 
 * <h2>Customization and allowed locations</h2>
 * 
 * - globalBinding/schemaBinding
 *     schema
 * 
 * - class
 *     complexType(*), modelGroupDecl, modelGroup, element
 * 
 * - property
 *     attribute, element, any, modelGroup, modelGroupRef, complexType(*)
 * 
 * - javaType
 *     simpleType(*)
 * 
 * - typesafeEnumClass
 *     simpleType(*)
 * 
 * - typesafeEnumMember
 *     simpleType(*), enumeration
 * 
 * Components marked with '*' needs a check by this component
 * since more than one schema element corresponds to one schema component
 * of that type.
 * 
 * <p>
 * For simple types, customizations are allowed only under the {@code <xs:simpleType>}
 * element, and for complex types they are allowed only under the
 * {@code <xs:cimplexType>} element.
 * 
 * <p>
 * So the bottom line is that it would be suffice if we just make sure 
 * that no customization will be attached under other elements of 
 * simple types and complex types. Those are:
 * 
 * - simpleType/restriction
 * - list
 * - union
 * - complexType/(simple or complex)Content
 * - complexType/(simple or complex)Content/(restriction of extension)
 * 
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class CustomizationContextChecker extends XMLFilterImpl {
    
    /** Keep names of all the ancestor elements. */
    private final Stack<QName> elementNames = new Stack<QName>();
    
    private final ErrorHandler errorHandler;
    
    private Locator locator;
    
    /** Set of element names that cannot have JAXB customizations. */
    private static final Set<String> prohibitedSchemaElementNames = new HashSet<String>();

    /**
     * @param _errorHandler
     *      Detected errors will be sent to this object.
     */
    public CustomizationContextChecker( ErrorHandler _errorHandler ) {
        this.errorHandler = _errorHandler;
    }
    
    static {
        prohibitedSchemaElementNames.add("restriction");
        prohibitedSchemaElementNames.add("extension");
        prohibitedSchemaElementNames.add("simpleContent");
        prohibitedSchemaElementNames.add("complexContent");
        prohibitedSchemaElementNames.add("list");
        prohibitedSchemaElementNames.add("union");
    }
    
    
    
    
    /** Gets the stack top. */
    private QName top() {
        return elementNames.peek();
    }
    
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        QName newElement = new QName(namespaceURI,localName);
        
        if( newElement.getNamespaceURI().equals(Const.JAXB_NSURI)
         && top().getNamespaceURI().equals(WellKnownNamespace.XML_SCHEMA) ) {
            // we hit a JAXB customization. the stack top should be
            // <xs:appinfo>
            if( elementNames.size()>=3 ) {
                // the above statement checks if the following statement doesn't
                // cause an exception.
                QName schemaElement = elementNames.get( elementNames.size()-3 );
                if( prohibitedSchemaElementNames.contains(schemaElement.getLocalPart()) ) {
                    // the owner schema element is in the wanted list.
                    errorHandler.error( new SAXParseException(
                        Messages.format(
                            Messages.ERR_UNACKNOWLEDGED_CUSTOMIZATION,
                            localName ),
                        locator ) );
                }
            }
            
            
        }
        
        elementNames.push(newElement);
        
        super.startElement(namespaceURI, localName, qName, atts );
    }

    public void endElement(String namespaceURI, String localName, String qName)
        throws SAXException {
            
        super.endElement(namespaceURI, localName, qName);
        
        elementNames.pop();
    }

    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

}

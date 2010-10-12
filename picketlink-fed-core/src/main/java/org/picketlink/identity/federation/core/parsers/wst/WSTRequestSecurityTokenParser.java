/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.picketlink.identity.federation.core.parsers.wst;

import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

import org.picketlink.identity.federation.core.exceptions.ParsingException;
import org.picketlink.identity.federation.core.parsers.ParserNamespaceSupport;
import org.picketlink.identity.federation.core.parsers.util.StaxParserUtil;
import org.picketlink.identity.federation.core.wstrust.wrappers.RequestSecurityToken;

/**
 * Parse the WS-Trust RequestSecurityToken
 * @author Anil.Saldhana@redhat.com
 * @since Oct 11, 2010
 */
public class WSTRequestSecurityTokenParser implements ParserNamespaceSupport
{
   public static final String LOCALPART = "RequestSecurityToken";
 
   public Object parse(XMLEventReader xmlEventReader) throws ParsingException
   {
      StartElement startElement = null;
      try
      {
         startElement = StaxParserUtil.getNextStartElement( xmlEventReader );
      }
      catch (XMLStreamException e)
      {
         throw new ParsingException( e );
      }
      
      RequestSecurityToken requestToken = new RequestSecurityToken();
      
      QName contextQName = new QName( "", "Context" );
      Attribute contextAttribute = startElement.getAttributeByName( contextQName );
      String contextValue = StaxParserUtil.getAttributeValue( contextAttribute );
      requestToken.setContext( contextValue ); 
      
      int index = 0;
      
      while( index < 2 )
      {
         try
         {
            StartElement subEvent = (StartElement) xmlEventReader.nextEvent();
            String tag = StaxParserUtil.getStartElementName( subEvent );
            if( tag.equals( "RequestType" ))
            { 
               String value = xmlEventReader.getElementText();
               requestToken.setRequestType( new URI( value ));  
            }
            else if( tag.equals( "TokenType" ))
            {
               String value = xmlEventReader.getElementText();
               requestToken.setTokenType( new URI( value ));
            } 
         }
         catch( XMLStreamException e )
         {
            throw new ParsingException( e );
         }
         catch (URISyntaxException e)
         {
            throw new ParsingException( e );
         } 
         index++;
      }
      
      return requestToken;
   }
 
   public boolean supports(QName qname)
   { 
      return false;
   } 
}
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
package org.picketlink.identity.federation.web.handlers.saml2;

import java.io.StringWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.apache.log4j.Logger;
import org.picketlink.identity.federation.api.saml.v2.request.SAML2Request;
import org.picketlink.identity.federation.api.saml.v2.response.SAML2Response;
import org.picketlink.identity.federation.core.exceptions.ConfigurationException;
import org.picketlink.identity.federation.core.exceptions.ProcessingException;
import org.picketlink.identity.federation.core.saml.v2.common.IDGenerator;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLURIConstants;
import org.picketlink.identity.federation.core.saml.v2.exceptions.IssueInstantMissingException;
import org.picketlink.identity.federation.core.saml.v2.holders.IDPInfoHolder;
import org.picketlink.identity.federation.core.saml.v2.holders.IssuerInfoHolder;
import org.picketlink.identity.federation.core.saml.v2.holders.SPInfoHolder;
import org.picketlink.identity.federation.core.saml.v2.interfaces.SAML2HandlerRequest;
import org.picketlink.identity.federation.core.saml.v2.interfaces.SAML2HandlerResponse;
import org.picketlink.identity.federation.core.saml.v2.interfaces.SAML2HandlerRequest.GENERATE_REQUEST_TYPE;
import org.picketlink.identity.federation.core.saml.v2.util.AssertionUtil;
import org.picketlink.identity.federation.core.saml.v2.util.StatementUtil;
import org.picketlink.identity.federation.saml.v2.assertion.AssertionType;
import org.picketlink.identity.federation.saml.v2.assertion.AttributeStatementType;
import org.picketlink.identity.federation.saml.v2.assertion.AttributeType;
import org.picketlink.identity.federation.saml.v2.assertion.EncryptedElementType;
import org.picketlink.identity.federation.saml.v2.assertion.NameIDType;
import org.picketlink.identity.federation.saml.v2.assertion.SubjectType;
import org.picketlink.identity.federation.saml.v2.protocol.AuthnRequestType;
import org.picketlink.identity.federation.saml.v2.protocol.ResponseType;
import org.picketlink.identity.federation.saml.v2.protocol.StatusType;
import org.picketlink.identity.federation.web.constants.GeneralConstants;
import org.picketlink.identity.federation.web.core.HTTPContext;
import org.picketlink.identity.federation.web.core.IdentityServer;
import org.picketlink.identity.federation.web.interfaces.IRoleValidator;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Handles for dealing with SAML2 Authentication
 * @author Anil.Saldhana@redhat.com
 * @since Oct 8, 2009
 */
public class SAML2AuthenticationHandler extends BaseSAML2Handler
{  
   private static Logger log = Logger.getLogger(SAML2AuthenticationHandler.class);
   private boolean trace = log.isTraceEnabled();
   
   private IDPAuthenticationHandler idp = new IDPAuthenticationHandler();
   private SPAuthenticationHandler sp = new SPAuthenticationHandler();
   
   public void handleRequestType(SAML2HandlerRequest request, SAML2HandlerResponse response) throws ProcessingException
   { 
      if(request.getSAML2Object() instanceof AuthnRequestType == false)
         return ;
      
      if(getType() == HANDLER_TYPE.IDP)
      {
         idp.handleRequestType(request, response);
      }
      else
      {
         sp.handleRequestType(request, response);
      } 
   }
 
   public void handleStatusResponseType(SAML2HandlerRequest request, SAML2HandlerResponse response)
         throws ProcessingException
   { 
      if(request.getSAML2Object() instanceof ResponseType == false)
         return ;
      
      if(getType() == HANDLER_TYPE.IDP)
      {
         idp.handleStatusResponseType(request, response);
      }
      else
      {
         sp.handleStatusResponseType(request, response);
      } 
   }

   public void generateSAMLRequest(SAML2HandlerRequest request, SAML2HandlerResponse response)
         throws ProcessingException
   { 
      if(GENERATE_REQUEST_TYPE.AUTH != request.getTypeOfRequestToBeGenerated())
         return;
      
      if(getType() == HANDLER_TYPE.IDP)
      {
         idp.generateSAMLRequest(request, response);
         response.setSendRequest(true);
      }
      else
      {
         sp.generateSAMLRequest(request, response);
         response.setSendRequest(true);
      } 
   }
   
   private class IDPAuthenticationHandler
   {
      public void generateSAMLRequest(SAML2HandlerRequest request, 
            SAML2HandlerResponse response) throws ProcessingException
      {
         
      }
      
      
      public void handleStatusResponseType( SAML2HandlerRequest request, 
             SAML2HandlerResponse response ) throws ProcessingException
      {  
      }
      
      @SuppressWarnings("unchecked")
      public void handleRequestType( SAML2HandlerRequest request, 
            SAML2HandlerResponse response ) throws ProcessingException
      { 
         HTTPContext httpContext = (HTTPContext) request.getContext();
         ServletContext servletContext = httpContext.getServletContext();
         
         AuthnRequestType art = (AuthnRequestType) request.getSAML2Object();
         HttpSession session = BaseSAML2Handler.getHttpSession(request);
         Principal userPrincipal = (Principal) session.getAttribute(GeneralConstants.PRINCIPAL_ID);
         if(userPrincipal == null)
            userPrincipal = httpContext.getRequest().getUserPrincipal();
         
         List<String> roles = (List<String>) session.getAttribute(GeneralConstants.ROLES_ID);
         try
         {
            Map<String,Object> attribs = (Map<String, Object>) request.getOptions().get(GeneralConstants.ATTRIBUTES);
            long assertionValidity = (Long) request.getOptions().get(GeneralConstants.ASSERTIONS_VALIDITY);
            String destination = art.getAssertionConsumerServiceURL();
            Document samlResponse = this.getResponse(destination,
                  userPrincipal, roles, request.getIssuer().getValue(),
                  attribs,
                  assertionValidity);
            
            //Update the Identity Server
            IdentityServer identityServer = (IdentityServer) servletContext.getAttribute(GeneralConstants.IDENTITY_SERVER);
            identityServer.stack().register(session.getId(), destination);
            
            response.setDestination(destination);
            response.setResultingDocument(samlResponse); 
         }
         catch(Exception e)
         {
            log.error("Exception in processing authentication:", e);
            throw new ProcessingException("authentication issue");
         }
      }
      
      public Document getResponse( String assertionConsumerURL,
            Principal userPrincipal,
            List<String> roles, 
            String identityURL,
            Map<String, Object> attribs, 
            long assertionValidity) 
      throws ConfigurationException, IssueInstantMissingException
      {
         Document samlResponseDocument = null;
         
         if(trace) 
            log.trace("AssertionConsumerURL=" + assertionConsumerURL + 
               "::assertion validity=" + assertionValidity);
         ResponseType responseType = null;     
         
         SAML2Response saml2Response = new SAML2Response();
               
         //Create a response type
         String id = IDGenerator.create("ID_");

         IssuerInfoHolder issuerHolder = new IssuerInfoHolder(identityURL); 
         issuerHolder.setStatusCode(JBossSAMLURIConstants.STATUS_SUCCESS.get());

         IDPInfoHolder idp = new IDPInfoHolder();
         idp.setNameIDFormatValue(userPrincipal.getName());
         idp.setNameIDFormat(JBossSAMLURIConstants.NAMEID_FORMAT_PERSISTENT.get());

         SPInfoHolder sp = new SPInfoHolder();
         sp.setResponseDestinationURI(assertionConsumerURL);
         responseType = saml2Response.createResponseType(id, sp, idp, issuerHolder);
         
         //Add information on the roles
         AssertionType assertion = (AssertionType) responseType.getAssertionOrEncryptedAssertion().get(0);

         AttributeStatementType attrStatement = StatementUtil.createAttributeStatement(roles);
         assertion.getStatementOrAuthnStatementOrAuthzDecisionStatement().add(attrStatement);
         
         //Add timed conditions
         saml2Response.createTimedConditions(assertion, assertionValidity);

         //Add in the attributes information
         if(attribs != null)
         {
            AttributeStatementType attStatement = StatementUtil.createAttributeStatement(attribs);
            assertion.getStatementOrAuthnStatementOrAuthzDecisionStatement().add(attStatement);
         } 
    
         //Lets see how the response looks like 
         if(log.isTraceEnabled())
         {
            StringWriter sw = new StringWriter();
            try
            {
               saml2Response.marshall(responseType, sw);
            }
            catch (JAXBException e)
            {
               log.trace(e);
            }
            catch (SAXException e)
            {
               log.trace(e);
            }
            log.trace("Response="+sw.toString()); 
         }
         try
         {
            samlResponseDocument = saml2Response.convert(responseType);
         }
         catch (Exception e)
         {
            if(trace)
               log.trace(e); 
         } 
         return samlResponseDocument; 
      }
   }
   
   private class SPAuthenticationHandler
   {
      public void generateSAMLRequest(SAML2HandlerRequest request, 
            SAML2HandlerResponse response) throws ProcessingException
      {
         String issuerValue = request.getIssuer().getValue();
         
         SAML2Request samlRequest = new SAML2Request();
         String id = IDGenerator.create("ID_");
         try
         {
            AuthnRequestType authn = samlRequest.createAuthnRequestType(id, 
                issuerValue, response.getDestination(), issuerValue);
            
            response.setResultingDocument(samlRequest.convert(authn));
            response.setSendRequest(true);
         }
         catch (Exception e)
         {
            throw new ProcessingException(e);
         }  
      }
      
      public void handleStatusResponseType( SAML2HandlerRequest request, 
            SAML2HandlerResponse response ) throws ProcessingException
      { 
         HTTPContext httpContext = (HTTPContext) request.getContext();
         ResponseType responseType = (ResponseType) request.getSAML2Object();
         List<Object> assertions = responseType.getAssertionOrEncryptedAssertion();
         if(assertions.size() == 0)
            throw new IllegalStateException("No assertions in reply from IDP"); 
         
         Object assertion = assertions.get(0);
         if(assertion instanceof EncryptedElementType)
         {
            responseType = this.decryptAssertion(responseType);
         }
         
         Principal userPrincipal = handleSAMLResponse(responseType, response);
         if(userPrincipal == null)
         {
            response.setError(403, "User Principal not determined: Forbidden");
         } 
         else
         {
            //add it to the session
            HttpSession session = httpContext.getRequest().getSession(false);
            session.setAttribute(GeneralConstants.PRINCIPAL_ID, userPrincipal);
         }
      }
      
      public void handleRequestType( SAML2HandlerRequest request,  
            SAML2HandlerResponse response ) throws ProcessingException
      {  
      }
      
      private ResponseType decryptAssertion(ResponseType responseType)
      {
         throw new RuntimeException("This authenticator does not handle encryption");
      }
      
      @SuppressWarnings("unchecked")
      private Principal handleSAMLResponse(ResponseType responseType, SAML2HandlerResponse response) 
      throws ProcessingException 
      { 
         if(responseType == null)
            throw new IllegalArgumentException("response type is null");
         
         StatusType statusType = responseType.getStatus();
         if(statusType == null)
            throw new IllegalArgumentException("Status Type from the IDP is null");

         String statusValue = statusType.getStatusCode().getValue();
         if(JBossSAMLURIConstants.STATUS_SUCCESS.get().equals(statusValue) == false)
            throw new SecurityException("IDP forbid the user");

         List<Object> assertions = responseType.getAssertionOrEncryptedAssertion();
         if(assertions.size() == 0)
            throw new IllegalStateException("No assertions in reply from IDP"); 
         
         AssertionType assertion = (AssertionType)assertions.get(0);
         //Check for validity of assertion
         boolean expiredAssertion;
         try
         {
            expiredAssertion = AssertionUtil.hasExpired(assertion);
         }
         catch (ConfigurationException e)
         {
           throw new ProcessingException(e);
         }
         if(expiredAssertion)
         {
            throw new ProcessingException("Assertion has expired");
         } 
         
         SubjectType subject = assertion.getSubject(); 
         JAXBElement<NameIDType> jnameID = (JAXBElement<NameIDType>) subject.getContent().get(0);
         NameIDType nameID = jnameID.getValue();
         final String userName = nameID.getValue();
         List<String> roles = new ArrayList<String>();

         //Let us get the roles
         AttributeStatementType attributeStatement = (AttributeStatementType) assertion.getStatementOrAuthnStatementOrAuthzDecisionStatement().get(0);
         List<Object> attList = attributeStatement.getAttributeOrEncryptedAttribute();
         for(Object obj:attList)
         {
            AttributeType attr = (AttributeType) obj;
            String roleName = (String) attr.getAttributeValue().get(0);
            roles.add(roleName);
         }
         
         response.setRoles(roles);
         
         Principal principal = new Principal()
         {
            public String getName()
            {
               return userName;
            }
         };
    
         if(handlerChainConfig.getParameter(GeneralConstants.ROLE_VALIDATOR_IGNORE) == null)
         {
            //Validate the roles
            IRoleValidator roleValidator = 
               (IRoleValidator) handlerChainConfig.getParameter(GeneralConstants.ROLE_VALIDATOR);
            if(roleValidator == null)
               throw new ProcessingException("Role Validator not provided");
            
            boolean validRole = roleValidator.userInRole(principal, roles);
            if(!validRole)
            {
               if(trace)
                  log.trace("Invalid role:" + roles);
               principal = null;
            }  
         }
         return principal;
      } 
   }
}
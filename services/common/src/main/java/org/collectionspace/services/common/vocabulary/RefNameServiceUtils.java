/**
 *  This document is a part of the source code and related artifacts
 *  for CollectionSpace, an open source collections management system
 *  for museums and related institutions:

 *  http://www.collectionspace.org
 *  http://wiki.collectionspace.org

 *  Copyright 2009 University of California at Berkeley

 *  Licensed under the Educational Community License (ECL), Version 2.0.
 *  You may not use this file except in compliance with this License.

 *  You may obtain a copy of the ECL 2.0 License at

 *  https://source.collectionspace.org/collection-space/LICENSE.txt

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.collectionspace.services.common.vocabulary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.model.impl.primitives.StringProperty;
import org.nuxeo.ecm.core.api.repository.RepositoryInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.collectionspace.services.client.PoxPayloadIn;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.common.ServiceMain;
import org.collectionspace.services.common.context.ServiceContext;
import org.collectionspace.services.common.context.AbstractServiceContextImpl;
import org.collectionspace.services.common.api.Tools;
import org.collectionspace.services.common.authorityref.AuthorityRefDocList;
import org.collectionspace.services.common.authorityref.AuthorityRefList;
import org.collectionspace.services.common.config.TenantBindingConfigReaderImpl;
import org.collectionspace.services.common.context.ServiceBindingUtils;
import org.collectionspace.services.common.document.DocumentException;
import org.collectionspace.services.common.document.DocumentNotFoundException;
import org.collectionspace.services.common.document.DocumentUtils;
import org.collectionspace.services.common.document.DocumentWrapper;
import org.collectionspace.services.common.repository.RepositoryClient;
import org.collectionspace.services.nuxeo.client.java.DocHandlerBase;
import org.collectionspace.services.nuxeo.client.java.RepositoryJavaClientImpl;
import org.collectionspace.services.common.security.SecurityUtils;
import org.collectionspace.services.common.service.ServiceBindingType;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.collectionspace.services.nuxeo.util.NuxeoUtils;

import com.sun.xml.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

/**
 * RefNameServiceUtils is a collection of services utilities related to refName usage.
 *
 * $LastChangedRevision: $
 * $LastChangedDate: $
 */
public class RefNameServiceUtils {

	public static class AuthRefConfigInfo {
		public String getQualifiedDisplayName() {
	        return(Tools.isBlank(schema))?
	        		displayName:DocumentUtils.appendSchemaName(schema, displayName);
		}
		public String getDisplayName() {
			return displayName;
		}
		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}
		String displayName;
		String schema;
		public String getSchema() {
			return schema;
		}
		public void setSchema(String schema) {
			this.schema = schema;
		}
		public String getFullPath() {
			return fullPath;
		}
		public void setFullPath(String fullPath) {
			this.fullPath = fullPath;
		}
		String fullPath;
		protected String[] pathEls;
		public AuthRefConfigInfo(AuthRefConfigInfo arci) {
			this.displayName = arci.displayName;
			this.schema = arci.schema;
			this.fullPath = arci.fullPath;
			this.pathEls = arci.pathEls;
			// Skip the pathElse check, since we are creatign from another (presumably valid) arci.
		}
		
		public AuthRefConfigInfo(String displayName, String schema, String fullPath, String[] pathEls) {
			this.displayName = displayName;
			this.schema = schema;
			this.fullPath = fullPath;
			this.pathEls = pathEls;
			checkPathEls();
		}

		// Split a config value string like "intakes_common:collector", or
		// "collectionobjects_common:contentPeoples|contentPeople"
		// "collectionobjects_common:assocEventGroupList/*/assocEventPlace"
		// If has a pipe ('|') second part is a displayLabel, and first is path
		// Otherwise, entry is a path, and can use the last pathElement as displayName
		// Should be schema qualified.
		public AuthRefConfigInfo(String configString) {
        	String[] pair = configString.split("\\|", 2);
        	String[] pathEls;
        	String displayName, fullPath;
        	if(pair.length == 1) {
        		// no label specifier, so we'll defer getting label
        		fullPath = pair[0];
        		pathEls = pair[0].split("/");
        		displayName = pathEls[pathEls.length-1];
        	} else {
        		fullPath = pair[0];
        		pathEls = pair[0].split("/");
        		displayName = pair[1];
        	}
        	String[] schemaSplit = pathEls[0].split(":",2);
        	String schema; 
        	if(schemaSplit.length==1) {	// schema not specified
        		schema = null;
        	} else {
        		schema = schemaSplit[0];
        		if(pair.length == 1 && pathEls.length == 1) {	// simplest case of field in top level schema, no labelll
        			displayName = schemaSplit[1];	// Have to fix up displayName to have no schema
        		}
        	}
			this.displayName = displayName;
			this.schema = schema;
			this.fullPath = fullPath;
			this.pathEls = pathEls;
			checkPathEls();
		}
		
		protected void checkPathEls() {
        	int len = pathEls.length;
        	if(len<1)
        		throw new InternalError("Bad values in authRef info - caller screwed up:"+fullPath);
        	// Handle case of them putting a leading slash on the path
        	if(len>1 && pathEls[0].endsWith(":")) {
        		len--;
        		String[] newArray = new String[len];
        		newArray[0] = pathEls[0]+pathEls[1];
        		if(len>=2) {
        			System.arraycopy(pathEls, 2, newArray, 1, len-1);
        		}
        		pathEls = newArray;
        	}
		}
	}

	public static class AuthRefInfo extends AuthRefConfigInfo {
		public Property getProperty() {
			return property;
		}
		public void setProperty(Property property) {
			this.property = property;
		}
		Property property;
		public AuthRefInfo(String displayName, String schema, String fullPath, String[] pathEls, Property prop) {
			super(displayName, schema, fullPath, pathEls);
			this.property = prop;
		}
		public AuthRefInfo(AuthRefConfigInfo arci, Property prop) {
			super(arci);
			this.property = prop;
		}
	}

    private static final Logger logger = LoggerFactory.getLogger(RefNameServiceUtils.class);
    
    private static ArrayList<String> refNameServiceTypes = null;
    
    public static List<AuthRefConfigInfo> getConfiguredAuthorityRefs(ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx) {
    	List<String> authRefFields =
                ((AbstractServiceContextImpl) ctx).getAllPartsPropertyValues(
                ServiceBindingUtils.AUTH_REF_PROP, ServiceBindingUtils.QUALIFIED_PROP_NAMES);
    	ArrayList<AuthRefConfigInfo> authRefsInfo = new ArrayList<AuthRefConfigInfo>(authRefFields.size()); 
    	for(String spec:authRefFields) {
    		AuthRefConfigInfo arci = new AuthRefConfigInfo(spec);
    		authRefsInfo.add(arci);
    	}
    	return authRefsInfo;
    }

    public static AuthorityRefDocList getAuthorityRefDocs(
    		RepositoryInstance repoSession,
    		ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx,
            RepositoryClient<PoxPayloadIn, PoxPayloadOut> repoClient,
            List<String> serviceTypes,
            String refName,
            String refPropName,
            int pageSize, int pageNum, boolean computeTotal)
            		throws DocumentException, DocumentNotFoundException {
    	AuthorityRefDocList wrapperList = new AuthorityRefDocList();
        AbstractCommonList commonList = (AbstractCommonList) wrapperList;
        commonList.setPageNum(pageNum);
        commonList.setPageSize(pageSize);
        List<AuthorityRefDocList.AuthorityRefDocItem> list =
                wrapperList.getAuthorityRefDocItem();
        
        Map<String, ServiceBindingType> queriedServiceBindings = new HashMap<String, ServiceBindingType>();
        Map<String, List<AuthRefConfigInfo>> authRefFieldsByService = new HashMap<String, List<AuthRefConfigInfo>>();

        RepositoryJavaClientImpl nuxeoRepoClient = (RepositoryJavaClientImpl)repoClient;
    	try {
	        DocumentModelList docList = findAuthorityRefDocs(ctx, repoClient, repoSession,
	        		serviceTypes, refName, refPropName, queriedServiceBindings, authRefFieldsByService, pageSize, pageNum, computeTotal);
	
	        if (docList == null) { // found no authRef fields - nothing to process
	            return wrapperList;
	        }
	        // Set num of items in list. this is useful to our testing framework.
	        commonList.setItemsInPage(docList.size());
	        // set the total result size
	        commonList.setTotalItems(docList.totalSize());
	        
	        int nRefsFound = processRefObjsDocList(docList, refName, queriedServiceBindings, authRefFieldsByService,
					       			list, null);
	        if(logger.isDebugEnabled() && (nRefsFound < docList.size())) {
	        	logger.debug("Internal curiosity: got fewer matches of refs than # docs matched...");
	        }
    	} catch (Exception e) {
			logger.error("Could not retrieve the Nuxeo repository", e);
			wrapperList = null;
    	}
	       
    	return wrapperList;
    }
    
    private static ArrayList<String> getRefNameServiceTypes() {
    	if(refNameServiceTypes == null) {
    		refNameServiceTypes = new ArrayList<String>();
    		refNameServiceTypes.add(ServiceBindingUtils.SERVICE_TYPE_AUTHORITY);
    		refNameServiceTypes.add(ServiceBindingUtils.SERVICE_TYPE_OBJECT);
    		refNameServiceTypes.add(ServiceBindingUtils.SERVICE_TYPE_PROCEDURE);
    	}
    	return refNameServiceTypes;
    }
    
    // Seems like a good value - no real data to set this well.
    private static final int N_OBJS_TO_UPDATE_PER_LOOP = 100;
    
    public static int updateAuthorityRefDocs(
    		ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx,
            RepositoryClient<PoxPayloadIn, PoxPayloadOut> repoClient,
            RepositoryInstance repoSession,
            String oldRefName,
            String newRefName,
            String refPropName ) throws Exception {
        Map<String, ServiceBindingType> queriedServiceBindings = new HashMap<String, ServiceBindingType>();
        Map<String, List<AuthRefConfigInfo>> authRefFieldsByService = new HashMap<String, List<AuthRefConfigInfo>>();
        int nRefsFound = 0;
        if(!(repoClient instanceof RepositoryJavaClientImpl)) {
    		throw new InternalError("updateAuthorityRefDocs() called with unknown repoClient type!");
        }
        try {
        	final int pageSize = N_OBJS_TO_UPDATE_PER_LOOP;	
        	int pageNumProcessed = 1;
        	while(true) {	// Keep looping until we find all the refs.
        		logger.debug("updateAuthorityRefDocs working on page: "+pageNumProcessed);
        		// Note that we always ask the Repo for the first page, since each page we process
        		// should not be found in successive searches. Slightly inefficient, but more
        		// reliable (stateless).
		        DocumentModelList docList = findAuthorityRefDocs(ctx, repoClient, repoSession,
		        		getRefNameServiceTypes(), oldRefName, refPropName,
		        		queriedServiceBindings, authRefFieldsByService, pageSize, 0, false);
		
		        if((docList == null) 			// found no authRef fields - nothing to do
		        	|| (docList.size() == 0)) {	// No more to handle
	        		logger.debug("updateAuthorityRefDocs no more results");
		            break;
		        }
        		logger.debug("updateAuthorityRefDocs curr page result list size: "+docList.size());
		        int nRefsFoundThisPage = processRefObjsDocList(docList, oldRefName, queriedServiceBindings, authRefFieldsByService,
						       			null, newRefName);
		        if(nRefsFoundThisPage>0) {
		        	((RepositoryJavaClientImpl)repoClient).saveDocListWithoutHandlerProcessing(ctx, repoSession, docList, true);
		        	nRefsFound += nRefsFoundThisPage;
		        }
		        pageNumProcessed++;
        	}
        } catch (Exception e) {
    		logger.error("Internal error updating the AuthorityRefDocs: " + e.getLocalizedMessage());
    		logger.debug(Tools.errorToString(e, true));
    		throw e;
        }
		logger.debug("updateAuthorityRefDocs replaced a total of: "+nRefsFound);
        return nRefsFound;
    }
    
    private static DocumentModelList findAuthorityRefDocs(
    		ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx,
            RepositoryClient<PoxPayloadIn, PoxPayloadOut> repoClient,
            RepositoryInstance repoSession,
            List<String> serviceTypes,
            String refName,
            String refPropName,
            Map<String, ServiceBindingType> queriedServiceBindings,
            Map<String, List<AuthRefConfigInfo>> authRefFieldsByService,
            int pageSize, int pageNum, boolean computeTotal) throws DocumentException, DocumentNotFoundException {

        // Get the service bindings for this tenant
        TenantBindingConfigReaderImpl tReader =
                ServiceMain.getInstance().getTenantBindingConfigReader();
        // We need to get all the procedures, authorities, and objects.
        List<ServiceBindingType> servicebindings = tReader.getServiceBindingsByType(ctx.getTenantId(), serviceTypes);
        if (servicebindings == null || servicebindings.isEmpty()) {
        	logger.error("RefNameServiceUtils.getAuthorityRefDocs: No services bindings found, cannot proceed!");
            return null;
        }
        // Filter the list for current user rights
        servicebindings = SecurityUtils.getReadableServiceBindingsForCurrentUser(servicebindings);
        
        // Need to escape the quotes in the refName
        // TODO What if they are already escaped?
        String escapedRefName = refName.replaceAll("'", "\\\\'");
        ArrayList<String> docTypes = new ArrayList<String>();
        
        String query = computeWhereClauseForAuthorityRefDocs(escapedRefName, refPropName, docTypes, servicebindings, 
        											queriedServiceBindings, authRefFieldsByService );
        if (query == null) { // found no authRef fields - nothing to query
            return null;
        }
        // Now we have to issue the search
        RepositoryJavaClientImpl nuxeoRepoClient = (RepositoryJavaClientImpl)repoClient;
        DocumentWrapper<DocumentModelList> docListWrapper = nuxeoRepoClient.findDocs(ctx, repoSession,
                docTypes, query, pageSize, pageNum, computeTotal);
        // Now we gather the info for each document into the list and return
        DocumentModelList docList = docListWrapper.getWrappedObject();
        return docList;
    }
    
    private static final boolean READY_FOR_COMPLEX_QUERY = true;
    
    private static String computeWhereClauseForAuthorityRefDocs(
    		String escapedRefName,
    		String refPropName,
    		ArrayList<String> docTypes,
    		List<ServiceBindingType> servicebindings,
    		Map<String, ServiceBindingType> queriedServiceBindings,
    		Map<String, List<AuthRefConfigInfo>> authRefFieldsByService ) {
        StringBuilder whereClause = new StringBuilder();
        boolean fFirst = true;
        List<String> authRefFieldPaths;
        for (ServiceBindingType sb : servicebindings) {
        	// Gets the property names for each part, qualified with the part label (which
        	// is also the table name, the way that the repository works).
            authRefFieldPaths =
                    ServiceBindingUtils.getAllPartsPropertyValues(sb,
                    		refPropName, ServiceBindingUtils.QUALIFIED_PROP_NAMES);
            if (authRefFieldPaths.isEmpty()) {
                continue;
            }
            ArrayList<AuthRefConfigInfo> authRefsInfo = new ArrayList<AuthRefConfigInfo>();
        	for(String spec:authRefFieldPaths) {
        		AuthRefConfigInfo arci = new AuthRefConfigInfo(spec);
        		authRefsInfo.add(arci);
        	}

            String docType = sb.getObject().getName();
            queriedServiceBindings.put(docType, sb);
            authRefFieldsByService.put(docType, authRefsInfo);
            docTypes.add(docType);
            for (AuthRefConfigInfo arci : authRefsInfo) {
                // Build up the where clause for each authRef field
            	if(!READY_FOR_COMPLEX_QUERY) {	// filter complex field references
            		if(arci.pathEls.length>1)
            			continue;				// skip this one
            	}
                if (fFirst) {
                    fFirst = false;
                } else {
                    whereClause.append(" OR ");
                }
                //whereClause.append(prefix);
                whereClause.append(arci.getFullPath());
                whereClause.append("='");
                whereClause.append(escapedRefName);
                whereClause.append("'");
            }
        }
        
        String whereClauseStr = whereClause.toString(); // for debugging
        if (logger.isTraceEnabled()) {
        	logger.trace("The 'where' clause of the xyz method is: ", whereClauseStr);
        }
        
        if (fFirst) { // found no authRef fields - nothing to query
            return null;
        } else {
        	return whereClause.toString(); 
        }
    }
    
    /*
     * Runs through the list of found docs, processing them. 
     * If list is non-null, then processing means gather the info for items.
     * If list is null, and newRefName is non-null, then processing means replacing and updating. 
     *   If processing/updating, this must be called in teh context of an open session, and caller
     *   must release Session after calling this.
     * 
     */
    private static int processRefObjsDocList(
    		DocumentModelList docList,
    		String refName,
    		Map<String, ServiceBindingType> queriedServiceBindings,
    		Map<String, List<AuthRefConfigInfo>> authRefFieldsByService,
   			List<AuthorityRefDocList.AuthorityRefDocItem> list, 
   			String newAuthorityRefName) {
        Iterator<DocumentModel> iter = docList.iterator();
        int nRefsFoundTotal = 0;
        while (iter.hasNext()) {
            DocumentModel docModel = iter.next();
            AuthorityRefDocList.AuthorityRefDocItem ilistItem;

            String docType = docModel.getDocumentType().getName();
            ServiceBindingType sb = queriedServiceBindings.get(docType);
            if (sb == null) {
                throw new RuntimeException(
                        "getAuthorityRefDocs: No Service Binding for docType: " + docType);
            }
            String serviceContextPath = "/" + sb.getName().toLowerCase() + "/";
            
            if(list == null) { // no list - should be update refName case.
            	if(newAuthorityRefName==null) {
            		throw new InternalError("processRefObjsDocList() called with neither an itemList nor a new RefName!");
        		}
            	ilistItem = null;
            } else {	// Have a list - refObjs case
            	if(newAuthorityRefName!=null) {
            		throw new InternalError("processRefObjsDocList() called with both an itemList and a new RefName!");
            	}
            	ilistItem = new AuthorityRefDocList.AuthorityRefDocItem();
                String csid = NuxeoUtils.getCsid(docModel);//NuxeoUtils.extractId(docModel.getPathAsString());
                ilistItem.setDocId(csid);
                ilistItem.setUri(serviceContextPath + csid);
                try {
                	ilistItem.setUpdatedAt(DocHandlerBase.getUpdatedAtAsString(docModel));
                } catch(Exception e) {
                	logger.error("Error getting udpatedAt value for doc ["+csid+"]: "+e.getLocalizedMessage());
                }
                // The id and URI are the same on all doctypes
                ilistItem.setDocType(docType);
                ilistItem.setDocNumber(
                        ServiceBindingUtils.getMappedFieldInDoc(sb, ServiceBindingUtils.OBJ_NUMBER_PROP, docModel));
                ilistItem.setDocName(
                        ServiceBindingUtils.getMappedFieldInDoc(sb, ServiceBindingUtils.OBJ_NAME_PROP, docModel));
            }
            // Now, we have to loop over the authRefFieldsByService to figure
            // out which field(s) matched this.
            List<AuthRefConfigInfo> matchingAuthRefFields = authRefFieldsByService.get(docType);
            if (matchingAuthRefFields == null || matchingAuthRefFields.isEmpty()) {
                throw new RuntimeException(
                        "getAuthorityRefDocs: internal logic error: can't fetch authRefFields for DocType.");
            }
            //String authRefAncestorField = "";
            //String authRefDescendantField = "";
            //String sourceField = "";
            int nRefsFoundInDoc = 0;
            
            ArrayList<RefNameServiceUtils.AuthRefInfo> foundProps 
            					= new ArrayList<RefNameServiceUtils.AuthRefInfo>();
            try {
	            findAuthRefPropertiesInDoc(docModel, matchingAuthRefFields, refName, foundProps);
	            for(RefNameServiceUtils.AuthRefInfo ari:foundProps) {
	        		if(ilistItem != null) {
	                	if(nRefsFoundInDoc == 0) {	// First one?
	            			ilistItem.setSourceField(ari.getQualifiedDisplayName());
	                	} else {	// duplicates from one object
	            			ilistItem = cloneAuthRefDocItem(ilistItem, ari.getQualifiedDisplayName());
	            		}
	        			list.add(ilistItem);
	        		} else {	// update refName case
	        			Property propToUpdate = ari.getProperty();
	        			propToUpdate.setValue(newAuthorityRefName);
	        		}
	        		nRefsFoundInDoc++;
	            }
            } catch (ClientException ce) {
                throw new RuntimeException(
                        "getAuthorityRefDocs: Problem fetching values from repo: " + ce.getLocalizedMessage());
            }
            if (nRefsFoundInDoc == 0) {
                throw new RuntimeException(
                        "getAuthorityRefDocs: Could not find refname in object:"
                        + docType + ":" + NuxeoUtils.getCsid(docModel));
            }
            nRefsFoundTotal += nRefsFoundInDoc;
        }
        return nRefsFoundTotal;
    }
    
    private static AuthorityRefDocList.AuthorityRefDocItem cloneAuthRefDocItem(
    		AuthorityRefDocList.AuthorityRefDocItem ilistItem, String sourceField) {
    	AuthorityRefDocList.AuthorityRefDocItem newlistItem = new AuthorityRefDocList.AuthorityRefDocItem();
    	newlistItem.setDocId(ilistItem.getDocId());
    	newlistItem.setDocName(ilistItem.getDocName());
    	newlistItem.setDocNumber(ilistItem.getDocNumber());
    	newlistItem.setDocType(ilistItem.getDocType());
    	newlistItem.setUri(ilistItem.getUri());
    	newlistItem.setSourceField(sourceField);
    	return newlistItem;
    }
    
    public static List<AuthRefInfo> findAuthRefPropertiesInDoc( 
    		DocumentModel docModel, 
    		List<AuthRefConfigInfo> authRefFieldInfo,
    		String refNameToMatch,
    		List<AuthRefInfo> foundProps
    		) {
    	// Assume that authRefFieldInfo is keyed by the field name (possibly mapped for UI)
    	// and the values are elPaths to the field, where intervening group structures in
    	// lists of complex structures are replaced with "*". Thus, valid paths include
    	// the following (note that the ServiceBindingUtils prepend schema names to configured values):
    	// "schemaname:fieldname"
    	// "schemaname:scalarlistname"
    	// "schemaname:complexfieldname/fieldname"
    	// "schemaname:complexlistname/*/fieldname"
    	// "schemaname:complexlistname/*/scalarlistname"
    	// "schemaname:complexlistname/*/complexfieldname/fieldname"
    	// "schemaname:complexlistname/*/complexlistname/*/fieldname"
    	// etc.
        for (AuthRefConfigInfo arci : authRefFieldInfo) {
            try {
            	// Get first property and work down as needed.
           		Property prop = docModel.getProperty(arci.pathEls[0]);
           		findAuthRefPropertiesInProperty(foundProps, prop, arci, 0, refNameToMatch);
            } catch(Exception e) {
            	logger.error("Problem fetching property: "+arci.pathEls[0]);
            }
        }
        return foundProps;
    }

    public static List<AuthRefInfo> findAuthRefPropertiesInProperty(
    		List<AuthRefInfo> foundProps,
    		Property prop, 
    		AuthRefConfigInfo arci,
    		int pathStartIndex,		// Supports recursion and we work down the path
    		String refNameToMatch
    		) {
    	if (pathStartIndex >= arci.pathEls.length) {
    		throw new ArrayIndexOutOfBoundsException("Index = "+pathStartIndex+" for path: "
    												+arci.pathEls.toString());
    	}
		AuthRefInfo ari = null;
   		if (prop == null) {
   			return foundProps;
   		}
   		
   		if (prop instanceof StringProperty) {	// scalar string
			addARIifMatches(refNameToMatch, arci, prop, foundProps);
   		} else if(prop instanceof List) {
   			List<Property> propList = (List<Property>)prop;
   			// run through list. Must either be list of Strings, or Complex
   			for (Property listItemProp : propList) {
   				if(listItemProp instanceof StringProperty) {
   					if(arci.pathEls.length-pathStartIndex != 1) {
   						logger.error("Configuration for authRefs does not match schema structure: "
   								+arci.pathEls.toString());
   						break;
   					} else {
   						addARIifMatches(refNameToMatch, arci, listItemProp, foundProps);
   					}
   				} else if(listItemProp.isComplex()) {	
   					// Just recurse to handle this. Note that since this is a list of complex, 
   					// which should look like listName/*/... we add 2 to the path start index 
   					findAuthRefPropertiesInProperty(foundProps, listItemProp, arci,
   							pathStartIndex+2, refNameToMatch);
   				} else {
   					logger.error("Configuration for authRefs does not match schema structure: "
   							+arci.pathEls.toString());
   					break;
   				}
   			}
   		} else if(prop.isComplex()) {
   			String localPropName = arci.pathEls[pathStartIndex];
   			try {
	   			Property localProp = prop.get(localPropName);
	   			// Now just recurse, pushing down the path 1 step
				findAuthRefPropertiesInProperty(foundProps, localProp, arci, 
												pathStartIndex, refNameToMatch);
   			} catch(PropertyNotFoundException pnfe) {
   				logger.error("Could not find property: ["+localPropName+"] in path: "+
   								arci.getFullPath());
   				// Fall through - ari will be null and we will continue...
   			}
		} else {
				logger.error("Configuration for authRefs does not match schema structure: "
						+arci.pathEls.toString());
		}

    	if (ari != null) {
    		foundProps.add(ari); //FIXME: REM - This is dead code.  'ari' is never touched after being initalized to null.  Why?
    	}
    	
    	return foundProps;
    }
    
    private static void addARIifMatches(
    		String refNameToMatch, 
    		AuthRefConfigInfo arci, 
    		Property prop, 
    		List<AuthRefInfo> foundProps) {
		// Need to either match a passed refName 
		// OR have no refName to match but be non-empty
    	try {
	    	String value = (String)prop.getValue();
			if(((refNameToMatch!=null) && refNameToMatch.equals(value))
	   				|| ((refNameToMatch==null) && Tools.notBlank(value))) {
				// Found a match
				logger.debug("Found a match on property: "+prop.getPath()+" with value: ["+value+"]");
				AuthRefInfo ari = new AuthRefInfo(arci, prop);
				foundProps.add(ari);
	   		}
    	} catch(PropertyException pe) {
			logger.debug("PropertyException on: "+prop.getPath()+pe.getLocalizedMessage());
    	}
    }

    /*
     * Identifies whether the refName was found in the supplied field.
     * If passed a new RefName, will set that into fields in which the old one was found.
     *
     * Only works for:
     * * Scalar fields
     * * Repeatable scalar fields (aka multi-valued fields)
     *
     * Does not work for:
     * * Structured fields (complexTypes)
     * * Repeatable structured fields (repeatable complexTypes)
    private static int refNameFoundInField(String oldRefName, Property fieldValue, String newRefName) {
    	int nFound = 0;
    	if (fieldValue instanceof List) {
    		List<Property> fieldValueList = (List) fieldValue;
    		for (Property listItemValue : fieldValueList) {
    			try {
    				if ((listItemValue instanceof StringProperty)
    						&& oldRefName.equalsIgnoreCase((String)listItemValue.getValue())) {
    					nFound++;
        				if(newRefName!=null) {
        					fieldValue.setValue(newRefName);
        				} else {
        					// We cannot quit after the first, if we are replacing values.
        					// If we are just looking (not replacing), finding one is enough.
        					break;
        				}
    				}
    			} catch( PropertyException pe ) {}
    		}
    	} else {
    		try {
    			if ((fieldValue instanceof StringProperty)
    					&& oldRefName.equalsIgnoreCase((String)fieldValue.getValue())) {
					nFound++;
    				if(newRefName!=null) {
    					fieldValue.setValue(newRefName);
    				}
    			}
    		} catch( PropertyException pe ) {}
    	}
    	return nFound;
    }
     */
}


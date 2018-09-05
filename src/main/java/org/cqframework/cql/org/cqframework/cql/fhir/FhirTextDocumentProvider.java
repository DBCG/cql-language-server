package org.cqframework.cql.org.cqframework.cql.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import org.cqframework.cql.TextDocumentProvider;
import org.eclipse.lsp4j.TextDocumentItem;
import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Library;
import org.hl7.fhir.instance.model.api.IBaseBundle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Bryn on 9/4/2018.
 */
public class FhirTextDocumentProvider implements TextDocumentProvider {
    protected FhirContext fhirContext;

    /*
         const data = {
            "resourceType": "Library",
            "version": "1.0.0",
            "status": "draft",
            "id" : id,
            "type": {
              "coding": [
                {
                  "code": "logic-library"
                }
              ]
            },
            "content": [
              {
                "contentType": "text/cql",
                "data": content
              }
            ]
        };
    */

    public FhirTextDocumentProvider() {
        this.fhirContext = FhirContext.forDstu3();
    }

    @Override
    public Iterable<TextDocumentItem> getDocuments(String rootUri) {
        String baseUri = getEndpoint(rootUri);
        IGenericClient fhirClient = this.fhirContext.newRestfulGenericClient(baseUri);
        IQuery<IBaseBundle> search = fhirClient.search().byUrl("Library");
        Bundle results = search.returnBundle(Bundle.class).execute();
        List<TextDocumentItem> libraries = new ArrayList<>();
        extractLibraries(baseUri, results, libraries);
        while (results.getLink(IBaseBundle.LINK_NEXT) != null) {
            results = fhirClient.loadPage().next(results).execute();
            extractLibraries(baseUri, results, libraries);
        }

        return libraries;
    }


    private void extractLibraries(String baseUri, Bundle bundle, List<TextDocumentItem> libraries) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Library library = (Library)entry.getResource();
            TextDocumentItem textDocument = extractTextDocument(baseUri, library);
            if (textDocument != null) {
                libraries.add(textDocument);
            }
        }
    }

    @Override
    public TextDocumentItem getDocument(String uri) {
        String baseUri = getEndpoint(uri);
        IGenericClient fhirClient = this.fhirContext.newRestfulGenericClient(baseUri);
        Library library = fhirClient.read().resource(Library.class).withUrl(uri).execute();
        if (library != null) {
            TextDocumentItem textDocument = extractTextDocument(baseUri, library);
            return textDocument;
        }

        return null;
    }

    private TextDocumentItem extractTextDocument(String baseUri, Library library) {
        if (library.getType().getCoding().get(0).getCode().equals("logic-library")) {
            for (Attachment content : library.getContent()) {
                // TODO: Could use this for any content type, would require a mapping from content type to LanguageServer LanguageId
                if (content.getContentType().equals("text/cql")) {
                    TextDocumentItem textDocumentItem = new TextDocumentItem();
                    textDocumentItem.setUri(baseUri + "/Library/" + library.getId());
                    textDocumentItem.setVersion(0); // TODO: Cannot assume version of the resource is tracked and/or relevant without making assumptions about the FHIR Server...
                    textDocumentItem.setLanguageId("cql");
                    textDocumentItem.setText(new String(content.getData(), StandardCharsets.UTF_8));
                    return textDocumentItem;
                }
                // TODO: Decompile ELM if no CQL is available?
            }
        }

        return null;
    }

    private String getEndpoint(String uri) {
        int index = uri.lastIndexOf("/Library");
        if (index > 0) {
            return uri.substring(0, index);
        }

        return uri;
    }
}

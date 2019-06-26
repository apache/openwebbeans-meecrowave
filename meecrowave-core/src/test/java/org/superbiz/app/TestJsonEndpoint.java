package org.superbiz.app;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/testjsonendpoint")
public class TestJsonEndpoint {


    @GET
    @Path("book")
    @Produces(MediaType.APPLICATION_JSON)
    public Book getBook() {
        return new Book("dummyisbn");
    }


    public static class Book {
        private String isbn;

        public Book(String isbn) {
            this.isbn = isbn;
        }

        public String getIsbn() {
            return isbn;
        }

        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }
    }
}

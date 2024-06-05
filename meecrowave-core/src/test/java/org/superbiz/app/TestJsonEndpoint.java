package org.superbiz.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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

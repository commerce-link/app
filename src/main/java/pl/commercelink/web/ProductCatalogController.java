package pl.commercelink.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

/** Nothing is served from here any more; the catalog pages live in the Catalog* controllers. Removed in task 15. */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class ProductCatalogController {
}

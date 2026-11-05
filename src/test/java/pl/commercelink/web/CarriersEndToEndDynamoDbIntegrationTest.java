package pl.commercelink.web;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;
import org.thymeleaf.context.Context;
import pl.commercelink.carriers.CarrierDictionary;
import pl.commercelink.carriers.CarrierDictionaryRepository;
import pl.commercelink.carriers.CarrierDictionaryWriter;
import pl.commercelink.carriers.CarrierMapping;
import pl.commercelink.carriers.CarrierTranslator;
import pl.commercelink.carriers.CarriersRepository;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.migration.V025_CreateCarrierTables;
import pl.commercelink.migration.V026_LocalDevelopmentCarriersSeed;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.testsupport.DynamoDbLocalImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Carriers end to end against DynamoDB Local: the migration and the local seed on an empty database, the way of an order
 * through the dictionaries (import, quote, label, shipped status) and the super admin page from request to rendered HTML.
 * The tests share one database, so each panel test edits a dictionary of its own.
 */
@Testcontainers(disabledWithoutDocker = true)
class CarriersEndToEndDynamoDbIntegrationTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String ALLEGRO_METHODS = CarrierDictionary.marketplaceOrderCarrierTexts("Allegro");
    private static final String ALLEGRO = CarrierDictionary.marketplaceStatusCarrierCodes("Allegro");
    private static final String CENEO_FORMS = CarrierDictionary.marketplaceOrderCarrierTexts("Ceneo");
    private static final String CENEO = CarrierDictionary.marketplaceStatusCarrierCodes("Ceneo");
    private static final String EMPIK = CarrierDictionary.marketplaceOrderCarrierTexts("Empik");
    private static final String MORELE = CarrierDictionary.marketplaceOrderCarrierTexts("Morele");
    private static final String FURGONETKA = CarrierDictionary.shippingLabelCarrierCodes("furgonetka");
    private static final String WZA = CarrierDictionary.shippingLabelCarrierCodes("allegro");

    @Container
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DynamoDbLocalImage.IMAGE).withExposedPorts(8000);

    static AmazonDynamoDB client;
    static CarriersRepository carriers;
    static CarrierDictionaryRepository dictionaries;
    static CarrierTranslator translator;

    MockMvc mvc;

    @BeforeAll
    static void migrateAnEmptyDatabase() {
        client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000), "eu-central-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("local", "local")))
                .build();
        carriers = new CarriersRepository(client);
        dictionaries = new CarrierDictionaryRepository(client);
        translator = new CarrierTranslator(dictionaries);
        new V025_CreateCarrierTables(client).createTables();
        new V026_LocalDevelopmentCarriersSeed(client).seed();
        // made-up marketplace and label vocabularies, for the dictionaries the local seed does not have
        put(CENEO_FORMS, "InPost=InPost", "DPD=DPD", "Poczta Polska=Poczta Polska");
        put(CENEO, "1=InPost", "1=InPost Kurier", "2=DHL", "3=DPD", "4=Poczta Polska");
        put(EMPIK, "INPOST=InPost", "DPD=DPD");
        put(MORELE, "2=InPost", "4=Orlen", "7=Poczta Polska");
        put(FURGONETKA, "inpost=InPost", "inpost_courier=InPost Kurier", "dhl=DHL", "dpd=DPD");
    }

    private static void put(String key, String... pairs) {
        CarrierDictionary dictionary = dictionaries.get(key);
        dictionary.setMappings(new ArrayList<>(Arrays.stream(pairs)
                .map(pair -> pair.split("=", 2))
                .map(parts -> new CarrierMapping(parts[0], parts[1]))
                .toList()));
        dictionaries.save(dictionary);
    }

    @BeforeEach
    void panel() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        CarriersController controller = new CarriersController(carriers, dictionaries,
                new CarrierDictionaryWriter(dictionaries), messages, marketplaces("Morele", "Allegro", "Ceneo", "Empik"),
                shippingIntegrations("furgonetka", "allegro"));
        // the HTML is rendered from the returned model (render), so the view itself writes nothing
        View noOutput = (model, request, response) -> { };
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers((name, locale) -> name.startsWith("redirect:") ? redirect(name) : noOutput)
                .build();
    }

    @Test
    void theMigrationCreatesTheTablesAndTheLocalSeedFillsThem() {
        // when
        List<String> tables = client.listTables().getTableNames();

        // then
        assertThat(tables).contains(CarrierDictionary.TABLE_NAME, "Carriers");
        assertThat(carriers.names()).contains("InPost", "InPost Kurier", "Allegro One", "Meest", "X-press");
        assertThat(translator.carrierFor(ALLEGRO, "AMBRO_EXPRESS")).isEqualTo("Ambro Express");
        assertThat(List.of(ALLEGRO_METHODS, ALLEGRO, CENEO_FORMS, CENEO, EMPIK, MORELE, FURGONETKA, WZA))
                .allSatisfy(dictionary -> assertThat(dictionaries.find(dictionary)).isPresent());
    }

    @Test
    void creatingTheTablesAgainKeepsADictionaryEditedOnTheDashboard() {
        // given
        CarrierDictionary edited = dictionaries.get(ALLEGRO_METHODS);
        edited.getMappings().add(new CarrierMapping("Paczka w Ruchu", "Orlen"));
        dictionaries.save(edited);

        // when
        new V025_CreateCarrierTables(client).createTables();

        // then
        assertThat(translator.carrierFor(ALLEGRO_METHODS, "Paczka w Ruchu")).isEqualTo("Orlen");
    }

    @Test
    void seedingAgainKeepsADictionaryEditedOnTheDashboard() {
        // given
        CarrierDictionary edited = dictionaries.get(ALLEGRO_METHODS);
        edited.getMappings().add(new CarrierMapping("Paczka w Ruchu", "Orlen"));
        dictionaries.save(edited);

        // when
        new V026_LocalDevelopmentCarriersSeed(client).seed();

        // then
        assertThat(translator.carrierFor(ALLEGRO_METHODS, "Paczka w Ruchu")).isEqualTo("Orlen");
        assertThat(dictionaries.get(ALLEGRO_METHODS).getVersion()).isEqualTo(edited.getVersion());
    }

    @Test
    void anAllegroParcelLockerOrderTravelsThroughItsDictionaries() {
        // when: import, quote at Furgonetka, shipped status back to Allegro
        String carrier = translator.carrierFor(ALLEGRO_METHODS, "Allegro Paczkomaty InPost 24/7");
        String service = translator.externalTextForShipmentCarrier(FURGONETKA, carrier, List.of(FURGONETKA)).orElseThrow();
        String status = translator.externalTextForShipmentCarrier(ALLEGRO, carrier, List.of(FURGONETKA, ALLEGRO_METHODS)).orElseThrow();

        // then
        assertThat(carrier).isEqualTo("InPost");
        assertThat(service).isEqualTo("inpost");
        assertThat(status).isEqualTo("INPOST");
    }

    @Test
    void aCourierLabelNamesTheCarrierAndTheMarketplacesGetTheirOwnCodes() {
        // when
        String carrier = translator.carrierFor(FURGONETKA, "inpost_courier");

        // then
        assertThat(carrier).isEqualTo("InPost Kurier");
        assertThat(translator.externalTextFor(ALLEGRO, carrier)).contains("INPOST");
        assertThat(translator.externalTextFor(CENEO, carrier)).contains("1");
        assertThat(translator.externalTextFor(FURGONETKA, carrier)).contains("inpost_courier");
    }

    @Test
    void anOlderShipmentHoldingALabelCodeStillGetsItsStatusCode() {
        // when
        List<String> sources = List.of(FURGONETKA, CENEO_FORMS);

        // then
        assertThat(translator.externalTextForShipmentCarrier(CENEO, "dhl", sources)).contains("2");
        assertThat(translator.externalTextForShipmentCarrier(CENEO, "my", sources)).isEmpty();
    }

    @Test
    void aSellersCeneoFormNameIsReadByItsLongestFragment() {
        // when
        String carrier = translator.carrierFor(CENEO_FORMS, "Poczta Polska - list polecony, płatność przelewem");

        // then
        assertThat(carrier).isEqualTo("Poczta Polska");
        assertThat(translator.externalTextFor(CENEO, carrier)).contains("4");
    }

    @Test
    void marketplaceCodesOfDigitsAndWholeWordsMatchOnlyExactly() {
        // when / then
        assertThat(translator.carrierFor(MORELE, "7")).isEqualTo("Poczta Polska");
        assertThat(translator.carrierFor(MORELE, "14")).isEqualTo("14");
        assertThat(translator.carrierFor(EMPIK, "dpd")).isEqualTo("DPD");
    }

    @Test
    void aWysylamZAllegroLabelOfAllegroOneComesBackToAllegroAsItsId() {
        // when
        String carrier = translator.carrierFor(WZA, "ALLEGRO");

        // then
        assertThat(carrier).isEqualTo("Allegro One");
        assertThat(translator.externalTextFor(ALLEGRO, carrier)).contains("ALLEGRO");
    }

    @Test
    void anUnknownCarrierTextPassesEveryPathUnchangedAndWithoutACode() {
        // given
        List<String> handTypedOrSellerTexts = List.of("my", "me", "Odbiór osobisty w sklepie", "Kurier własny 24h", "123");

        // when / then
        for (String unknownText : handTypedOrSellerTexts) {
            assertThat(translator.carrierFor(ALLEGRO_METHODS, unknownText)).isEqualTo(unknownText);
            assertThat(translator.carrierFor(MORELE, unknownText)).isEqualTo(unknownText);
            assertThat(translator.carrierFor(FURGONETKA, unknownText)).isEqualTo(unknownText);
            assertThat(translator.externalTextForShipmentCarrier(FURGONETKA, unknownText, List.of(FURGONETKA))).isEmpty();
            assertThat(translator.externalTextForShipmentCarrier(ALLEGRO, unknownText, List.of(FURGONETKA, ALLEGRO_METHODS))).isEmpty();
            assertThat(translator.externalTextForShipmentCarrier(CENEO, unknownText, List.of(FURGONETKA, CENEO_FORMS))).isEmpty();
        }
    }

    @Test
    void ordersFromSourcesWithoutDictionariesKeepTheirTextAndGetNoStatusCode() {
        // given: order sources seen besides the connected marketplaces
        List<String> sources = List.of("CsCartMultiVendor", "OLX", "amazon.pl", "https://www.amazon.pl/");

        // when / then
        for (String source : sources) {
            assertThat(translator.carrierFor(CarrierDictionary.marketplaceOrderCarrierTexts(source), "DPD")).isEqualTo("DPD");
            assertThat(translator.externalTextFor(CarrierDictionary.marketplaceStatusCarrierCodes(source), "DPD")).isEmpty();
        }
    }

    @Test
    void theSuperAdminAddsACarrierOnceAndCannotAddItTwice() throws Exception {
        // when
        mvc.perform(post(CarriersController.PATH).param("name", "  Pocztex   Kurier48 ").locale(PL))
                .andExpect(redirectedUrl(CarriersController.PATH))
                .andExpect(flash().attribute("settingsSavedMessage", "Dodano przewoźnika Pocztex Kurier48."));
        MvcResult again = mvc.perform(post(CarriersController.PATH).param("name", "Pocztex Kurier48").locale(PL))
                .andReturn();

        // then
        assertThat(carriers.names()).contains("Pocztex Kurier48");
        assertThat(again.getFlashMap().get("settingsErrorMessage")).isEqualTo("Przewoźnik Pocztex Kurier48 już istnieje.");
    }

    @Test
    void aNameDifferingOnlyInCaseIsTheSameCarrier() throws Exception {
        // when
        MvcResult refused = mvc.perform(post(CarriersController.PATH).param("name", "inpost").locale(PL)).andReturn();

        // then
        assertThat(refused.getFlashMap().get("settingsErrorMessage")).isEqualTo("Przewoźnik inpost już istnieje.");
        assertThat(carriers.names()).filteredOn(name -> name.equalsIgnoreCase("InPost")).containsExactly("InPost");
    }

    @Test
    void theCarriersPageListsCarriersAndTheDictionariesOfEveryInstalledAdapter() throws Exception {
        // when
        MvcResult result = mvc.perform(get(CarriersController.PATH).locale(PL))
                .andExpect(status().isOk())
                .andExpect(view().name("carriers"))
                .andReturn();
        String html = render("carriers", result);

        // then
        assertThat(result.getModelAndView().getModel().get("dictionaries"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(CarriersController.DictionaryRow.class))
                .extracting(CarriersController.DictionaryRow::key)
                .containsExactly(ALLEGRO_METHODS, ALLEGRO, CENEO_FORMS, CENEO, EMPIK,
                        CarrierDictionary.marketplaceStatusCarrierCodes("Empik"), MORELE, CarrierDictionary.marketplaceStatusCarrierCodes("Morele"),
                        WZA, FURGONETKA);
        assertThat(html).doesNotContain("??").contains("InPost Kurier").contains("Allegro One")
                .contains("global#marketplace#allegro#import").contains("global#shipping#furgonetka")
                .contains("Ceneo: teksty przewoźnika w importowanym zamówieniu")
                .contains("/dashboard/carriers/dictionary?dictionary=global%23marketplace%23ceneo%23import");
    }

    @Test
    void anAdapterThatIsNotInstalledHasNoDictionaryPage() throws Exception {
        // when / then
        mvc.perform(get(CarriersController.DICTIONARY_PATH)
                        .param("dictionary", CarrierDictionary.marketplaceOrderCarrierTexts("Erli")))
                .andExpect(status().isNotFound());
    }

    @Test
    void reorderingAndAddingPairsIsSavedAsOneNewVersion() throws Exception {
        // given
        CarrierDictionary before = dictionaries.get(MORELE);

        // when
        mvc.perform(dictionaryPost(MORELE, before.getVersion(),
                        "4=Orlen", "2=InPost", "5=Orlen", "6=Zabka", "7=Poczta Polska", "8=DPD", "="))
                .andExpect(redirectedUrl(CarriersController.dictionaryHref(MORELE)));

        // then
        CarrierDictionary after = dictionaries.get(MORELE);
        assertThat(after.getVersion()).isEqualTo(before.getVersion() + 1);
        assertThat(after.getMappings()).extracting(m -> m.getExternalText() + "=" + m.getCarrier())
                .containsExactly("4=Orlen", "2=InPost", "5=Orlen", "6=Zabka", "7=Poczta Polska", "8=DPD");
        assertThat(translator.carrierFor(MORELE, "8")).isEqualTo("DPD");
    }

    @Test
    void thePageAfterASaveShowsTheSavedVersionWithoutReadingItBack() throws Exception {
        // given
        CarrierDictionary before = dictionaries.get(MORELE);
        MvcResult saved = mvc.perform(dictionaryPost(MORELE, before.getVersion(), "8=DPD", "2=InPost")).andReturn();

        // when
        MvcResult page = mvc.perform(get(CarriersController.DICTIONARY_PATH).param("dictionary", MORELE).locale(PL)
                        .flashAttrs(saved.getFlashMap()))
                .andExpect(status().isOk()).andReturn();

        // then
        String html = render("carrier-dictionary", page);
        assertThat(html).contains("name=\"version\" value=\"" + (before.getVersion() + 1) + "\"");
        assertThat(html.indexOf("value=\"2\"")).isGreaterThan(html.indexOf("value=\"8\""));
    }

    @Test
    void aSaveFromAPageReadBeforeSomeoneElsesSaveIsRefusedAndKeepsTheirs() throws Exception {
        // given: two operators open the page, the first one saves
        Long seen = dictionaries.get(WZA).getVersion();
        mvc.perform(dictionaryPost(WZA, seen, "DPD=DPD", "ALLEGRO=Allegro One"));

        // when
        MvcResult stale = mvc.perform(dictionaryPost(WZA, seen, "DHL=DHL")).andExpect(status().isOk()).andReturn();

        // then
        assertThat(stale.getModelAndView().getModel().get("settingsErrorMessage"))
                .isEqualTo("Ktoś zapisał ten słownik w międzyczasie. Odśwież stronę i wprowadź zmiany jeszcze raz.");
        assertThat(dictionaries.get(WZA).getMappings()).extracting(CarrierMapping::getExternalText).containsExactly("DPD", "ALLEGRO");
        assertThat(render("carrier-dictionary", stale)).contains("value=\"DHL\"");
    }

    @Test
    void aHalfFilledPairIsRefusedWithThePairsTheOperatorTyped() throws Exception {
        // given
        CarrierDictionary before = dictionaries.get(ALLEGRO_METHODS);

        // when
        MvcResult refused = mvc.perform(dictionaryPost(ALLEGRO_METHODS, before.getVersion(), "Paczkomat=InPost", "One Box="))
                .andExpect(status().isOk()).andReturn();

        // then
        assertThat(refused.getModelAndView().getModel().get("settingsErrorMessage")).asString().contains("One Box");
        assertThat(dictionaries.get(ALLEGRO_METHODS).getVersion()).isEqualTo(before.getVersion());
        assertThat(render("carrier-dictionary", refused)).contains("value=\"One Box\"");
    }

    @Test
    void theDictionaryPageShowsThePairsInOrderWithAnEmptyRowToAddOne() throws Exception {
        // when
        MvcResult result = mvc.perform(get(CarriersController.DICTIONARY_PATH).param("dictionary", CENEO).locale(PL))
                .andExpect(status().isOk())
                .andExpect(view().name("carrier-dictionary"))
                .andReturn();
        String html = render("carrier-dictionary", result);

        // then
        assertThat(html).doesNotContain("??").contains("Ceneo: kody przewoźnika przy statusie „wysłane” i w zwrotach");
        assertThat(html.indexOf("value=\"4\"")).isGreaterThan(html.indexOf("value=\"1\""));
        assertThat(org.apache.commons.lang3.StringUtils.countMatches(html, "data-carrier-row>"))
                .isEqualTo(dictionaries.get(CENEO).getMappings().size() + 1);
        assertThat(html).contains("name=\"version\" value=\"" + dictionaries.get(CENEO).getVersion() + "\"");
    }

    @Test
    void onlyTheSharedDictionariesAreEditedHere() throws Exception {
        // when / then
        mvc.perform(get(CarriersController.DICTIONARY_PATH)
                        .param("dictionary", CarrierDictionary.supplierDeliveryMethods("store-1", "Shoper-1")))
                .andExpect(status().isNotFound());
    }

    private static MarketplaceProviderFactory marketplaces(String... names) {
        MarketplaceProviderFactory factory = mock(MarketplaceProviderFactory.class);
        List<MarketplaceProviderDescriptor> descriptors = new ArrayList<>();
        for (String name : names) {
            MarketplaceProviderDescriptor descriptor = mock(MarketplaceProviderDescriptor.class);
            when(descriptor.name()).thenReturn(name);
            when(descriptor.displayName()).thenReturn(name);
            descriptors.add(descriptor);
        }
        when(factory.availableProviders()).thenReturn(descriptors);
        return factory;
    }

    private static ShippingProviderFactory shippingIntegrations(String... names) {
        ShippingProviderFactory factory = mock(ShippingProviderFactory.class);
        List<ShippingProviderDescriptor> descriptors = new ArrayList<>();
        for (String name : names) {
            ShippingProviderDescriptor descriptor = mock(ShippingProviderDescriptor.class);
            when(descriptor.name()).thenReturn(name);
            when(descriptor.displayName()).thenReturn(name);
            descriptors.add(descriptor);
        }
        when(factory.availableProviders()).thenReturn(descriptors);
        return factory;
    }

    private static MockHttpServletRequestBuilder dictionaryPost(String dictionary, Long version, String... pairs) {
        MockHttpServletRequestBuilder request = post(CarriersController.DICTIONARY_PATH)
                .param("dictionary", dictionary)
                .param("version", version == null ? "" : version.toString())
                .locale(PL);
        for (String pair : pairs) {
            String[] parts = pair.split("=", -1);
            request.param("externalText", parts[0]).param("carrier", parts[1]);
        }
        return request;
    }

    private static View redirect(String viewName) {
        RedirectView redirect = new RedirectView(viewName.substring("redirect:".length()), true);
        redirect.setExposeModelAttributes(false);
        return redirect;
    }

    private static String render(String template, MvcResult result) {
        Context context = new Context(PL);
        Map<String, Object> model = result.getModelAndView().getModel();
        context.setVariables(model);
        return EnglishFragmentTemplateEngine.create(PL).process(template, context);
    }
}

package com.foo.rest.emb.json.proxyprint;

import com.google.gson.Gson;
import org.evomaster.client.java.utils.SimpleLogger;

import java.io.IOException;
import java.util.*;

public class PrintRequestController {

    private Map<Long, PrintShop> printShops = new HashMap<>();

    private static SimpleLogger logger = new SimpleLogger();

    public Map<Long, String> calcBudgetForPrintRequest(String requestJSON) throws IOException {
        PrintRequest printRequest = new PrintRequest();

        List<Long> pshopIDs = null;
        Map prequest = new Gson().fromJson(requestJSON, Map.class);

        // PrintShops
        List<Double> tmpPshopIDs = (List<Double>) prequest.get("printshops");
        pshopIDs = new ArrayList<>();
        for (double doubleID : tmpPshopIDs) {
            pshopIDs.add((long) Double.valueOf((double) doubleID).intValue());
        }

        // Finally, calculate the budgets :D
        List<PrintShop> pshops = getListOfPrintShops(pshopIDs);
        Map<Long, String> budgets = printRequest.calcBudgetsForPrintShops(pshops);

        return budgets;
    }

    public List<PrintShop> getListOfPrintShops(List<Long> pshopsIDs) {
        List<PrintShop> pshops = new ArrayList<>();
        for (long pid : pshopsIDs) {
            pshops.add(printShops.get(pid));
        }
        return pshops;
    }

}

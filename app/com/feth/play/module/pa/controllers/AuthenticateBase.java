package com.feth.play.module.pa.controllers;

import utils.LegacyResponse;

public class AuthenticateBase {
    public static void noCache(LegacyResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
}

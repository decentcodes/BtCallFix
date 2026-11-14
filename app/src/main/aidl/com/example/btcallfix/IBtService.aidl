package com.example.btcallfix;

interface IBtService {
    void destroy() = 16777114;
    String setCallProfile(String address, boolean allowed) = 1;
}

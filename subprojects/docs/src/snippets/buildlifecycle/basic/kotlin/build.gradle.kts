println("Executed during the 2. CONFIGURATION PHASE.")

tasks.register("test") {
    doLast {
        println("test: Executed during the 3. EXECUTION PHASE.")
    }
}

tasks.register("testBoth") {
    doFirst {
        println("testBoth: Executed during the 3. EXECUTION PHASE.")
    }
    doLast {
        println("testBoth: Executed during the 3. EXECUTION PHASE.")
    }
    println("testBoth: Executed during the 2. CONFIGURATION PHASE.")
}

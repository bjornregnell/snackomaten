package snackomaten 

class TestSecureKeyValueStore extends munit.FunSuite:
  test("key-value pairs added to empty store and stored kvs read and attempting multiple openings"):
    val pw = "pwdOnlyForTesting"
    val pwFile = java.io.File("tmp/mpw.txt")
    if pwFile.exists() then pwFile.delete()

    val vaultFile = java.io.File("tmp/vlt.txt")
    if vaultFile.exists() then vaultFile.delete()
    
    val updatesFile = java.io.File("tmp/upd.txt")
    if updatesFile.exists() then updatesFile.delete()

    def openVault() = SecureKeyValueStore.Vault.openVaultOf[String, Tuple2[Int, Long]](
      masterPw = pw,
      mpwFile = pwFile,
      vaultFile = vaultFile,
      tmpFile = updatesFile, 
    )

    val v1 = openVault()
    assert(v1.isMasterPasswordFileCreated)
    val pairs = List("a" -> (1 -> 2L), "b" -> (3 -> 4L))
    val store1 = v1.vaultOpt.get

    for (k, v) <- pairs do 
      store1.addOneAndSavePending(k, v)

    intercept[AssertionError](openVault()) // prevented by .LOCK file

    val obtained = pairs.map((k, v) => k -> store1.get(k).get).sorted
    val expected = pairs.sorted

    assertEquals(obtained, expected)

    store1.close() // will clean up .LOCK file

    val store2 = openVault().vaultOpt.get

    for (k, v) <- pairs do 
      store2.addOneAndSavePending(k, v)

    val obtained2 = pairs.map((k, v) => k -> store2.get(k).get).sorted
    val expected2 = (pairs ++ pairs).sorted

    assert(store2.lockFile.exists())

    store2.close()

    assert(!store2.lockFile.exists())

    assertEquals(obtained, expected)




end TestSecureKeyValueStore
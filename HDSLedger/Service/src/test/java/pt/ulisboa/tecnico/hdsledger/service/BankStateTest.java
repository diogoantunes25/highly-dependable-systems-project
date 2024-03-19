package pt.ulisboa.tecnico.hdsledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequest;
import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Optional;

import com.google.gson.Gson;

public class BankStateTest {
    private static LedgerMessage createLedgerMessage(int id, Message.Type type, String message) {
        LedgerMessage ledgerMessage = new LedgerMessage(id, type);
        ledgerMessage.setMessage(message);
        ledgerMessage.signSelf(String.format("/tmp/priv_%d.key", id));
        return ledgerMessage;
    }

	private static LedgerMessage createTransferRequest(int requestId, int source, int destination, int amount) {
		String sourcePublicKey = String.format("/tmp/pub_%d.key", source);
		String destinationPublicKey = String.format("/tmp/pub_%d.key", destination);
        TransferRequest transferRequest = new TransferRequest(sourcePublicKey, destinationPublicKey, amount, 0, requestId);
        return createLedgerMessage(source, Message.Type.TRANSFER_REQUEST, new Gson().toJson(transferRequest));
	}

	// no real encryption is tested, only one client key is generated to be used
	// where needed
	@BeforeAll
	private static void genKeys() {
		int n = 5;
		List<String> publicKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());

		List<String> privateKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/priv_%d.key", i))
			.collect(Collectors.toList());

		for (int i = 0 ; i < n; i++) {
			try {
				RSAKeyGenerator.write(privateKeys.get(i), publicKeys.get(i));
			} catch (GeneralSecurityException | IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	// Returns hash of public key with id i
	private static String numberToId(int i) {
		return SigningUtils.publicKeyHash(String.format("/tmp/pub_%d.key", i));
	}

	@Test
	public void simpleTransfer() {
		BankState state = new BankState();

		int source = 4; // client
		String sourceId = numberToId(source);

		int destination = 3; // a replica
		String destinationId = numberToId(destination);

		int amount = 5;
		int seq = 1;

		System.out.println(state);

		state.spawnMoney(sourceId, amount);

		System.out.println(state);

		LedgerMessage request = createTransferRequest(seq, source, destination, amount);
		BankCommand cmd = new BankCommand(seq, sourceId, destinationId, amount, request); 

		assertEquals(Optional.of(1), state.update(cmd));

		System.out.println(state);

		Map<String, Integer> balances = state.getState();

		assertEquals(0, balances.get(sourceId));
		assertEquals(amount, balances.get(destinationId));
		
	}

	@Test
	public void multipleTransfers() {
		BankState state = new BankState();

		int A = 4;
		String AId = numberToId(A);

		int B = 3;
		String BId = numberToId(B);

		System.out.println(state);

		state.spawnMoney(AId, 10);

		System.out.println(state);

		LedgerMessage request1 = createTransferRequest(1, A, B, 5);
		BankCommand cmd1 = new BankCommand(1, AId, BId, 5, request1); 

		LedgerMessage request2 = createTransferRequest(2, A, B, 4);
		BankCommand cmd2 = new BankCommand(2, AId, BId, 4, request2); 

		LedgerMessage request3 = createTransferRequest(1, B, A, 3);
		BankCommand cmd3 = new BankCommand(1, BId, AId, 3, request3); 

		assertEquals(Optional.of(1), state.update(cmd1));
		System.out.println(state);

		assertEquals(Optional.of(2), state.update(cmd2));
		System.out.println(state);

		assertEquals(Optional.of(3), state.update(cmd3));
		System.out.println(state);

		Map<String, Integer> balances = state.getState();

		assertEquals(4, balances.get(AId));
		assertEquals(6, balances.get(BId));
	}

	@Test
	public void overspend() {
		BankState state = new BankState();

		int A = 4;
		String AId = numberToId(A);

		int B = 3;
		String BId = numberToId(B);

		System.out.println(state);

		state.spawnMoney(AId, 10);

		System.out.println(state);

		LedgerMessage request1 = createTransferRequest(1, A, B, 5);
		BankCommand cmd1 = new BankCommand(1, AId, BId, 5, request1); 

		LedgerMessage request2 = createTransferRequest(2, A, B, 6);
		BankCommand cmd2 = new BankCommand(2, AId, BId, 6, request2); 

		LedgerMessage request3 = createTransferRequest(1, B, A, 3);
		BankCommand cmd3 = new BankCommand(1, BId, AId, 3, request3); 

		assertEquals(Optional.of(1), state.update(cmd1));
		System.out.println(state);

		assertEquals(Optional.empty(), state.update(cmd2));
		System.out.println(state);

		assertEquals(Optional.of(2), state.update(cmd3));
		System.out.println(state);

		Map<String, Integer> balances = state.getState();

		assertEquals(8, balances.get(AId));
		assertEquals(2, balances.get(BId));
		
	}

	@Test
	public void replayTranscation() {
		BankState state = new BankState();

		int A = 4;
		String AId = numberToId(A);

		int B = 3;
		String BId = numberToId(B);

		System.out.println(state);

		state.spawnMoney(AId, 10);

		System.out.println(state);

		LedgerMessage request1 = createTransferRequest(1, A, B, 5);
		BankCommand cmd1 = new BankCommand(1, AId, BId, 5, request1); 

		LedgerMessage request2 = createTransferRequest(2, A, B, 4);
		BankCommand cmd2 = new BankCommand(2, AId, BId, 4, request2); 

		LedgerMessage request3 = createTransferRequest(1, B, A, 3);
		BankCommand cmd3 = new BankCommand(1, BId, AId, 3, request3); 

		assertEquals(Optional.of(1), state.update(cmd1));
		System.out.println(state);

		assertEquals(Optional.of(2), state.update(cmd2));
		System.out.println(state);

		assertEquals(Optional.empty(), state.update(cmd2));
		System.out.println(state);

		assertEquals(Optional.of(3), state.update(cmd3));
		System.out.println(state);

		Map<String, Integer> balances = state.getState();

		assertEquals(4, balances.get(AId));
		assertEquals(6, balances.get(BId));
	}
}

package pt.ulisboa.tecnico.hdsledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;

import pt.ulisboa.tecnico.hdsledger.communication.ledger.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.MessageCreator;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Optional;

public class BankStateTest {

	// no real encryption is tested, only one client key is generated to be used
	// where needed
	@BeforeAll
	public static void genKeys() throws GeneralSecurityException, IOException {
		int n = 5;
		List<String> publicKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/pub_%d.key", i))
			.collect(Collectors.toList());

		List<String> privateKeys = IntStream.range(0, n)
			.mapToObj(i -> String.format("/tmp/priv_%d.key", i))
			.collect(Collectors.toList());

		for (int i = 0 ; i < n; i++) {
			try {
				RSAKeyGenerator.read(publicKeys.get(i), "pub");
				RSAKeyGenerator.read(privateKeys.get(i), "priv");
			} catch (GeneralSecurityException | IOException e) {
				RSAKeyGenerator.write(privateKeys.get(i), publicKeys.get(i));
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

		LedgerMessage request = MessageCreator.createTransferRequest(seq, source, destination, amount);
		BankCommand cmd = new BankCommand(source, seq, sourceId, destinationId, amount, sourceId, 0, request); 

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

		LedgerMessage request1 = MessageCreator.createTransferRequest(1, A, B, 5);
		BankCommand cmd1 = new BankCommand(A, 1, AId, BId, 5, AId, 0, request1); 

		LedgerMessage request2 = MessageCreator.createTransferRequest(2, A, B, 4);
		BankCommand cmd2 = new BankCommand(A, 2, AId, BId, 4, AId, 0, request2); 

		LedgerMessage request3 = MessageCreator.createTransferRequest(1, B, A, 3);
		BankCommand cmd3 = new BankCommand(A, 1, BId, AId, 3, AId, 0, request3); 

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

		LedgerMessage request1 = MessageCreator.createTransferRequest(1, A, B, 5);
		BankCommand cmd1 = new BankCommand(A, 1, AId, BId, 5, AId, 0, request1); 

		LedgerMessage request2 = MessageCreator.createTransferRequest(2, A, B, 6);
		BankCommand cmd2 = new BankCommand(A, 2, AId, BId, 6, AId, 0, request2); 

		LedgerMessage request3 = MessageCreator.createTransferRequest(1, B, A, 3);
		BankCommand cmd3 = new BankCommand(B, 1, BId, AId, 3, AId, 0, request3); 

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

		LedgerMessage request1 = MessageCreator.createTransferRequest(1, A, B, 5);
		BankCommand cmd1 = new BankCommand(A, 1, AId, BId, 5, AId, 0, request1); 

		LedgerMessage request2 = MessageCreator.createTransferRequest(2, A, B, 4);
		BankCommand cmd2 = new BankCommand(A, 2, AId, BId, 4, AId, 0, request2); 

		LedgerMessage request3 = MessageCreator.createTransferRequest(1, B, A, 3);
		BankCommand cmd3 = new BankCommand(B, 1, BId, AId, 3, AId, 0, request3); 

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

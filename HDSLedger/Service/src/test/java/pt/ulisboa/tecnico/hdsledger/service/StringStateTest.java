package pt.ulisboa.tecnico.hdsledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ledger.AppendRequest;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;
import pt.ulisboa.tecnico.hdsledger.pki.SigningUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;

public class StringStateTest {
	private AppendMessage createAppendRequestMessage(int id, int receiver, String value, int sequenceNumber) {
		AppendRequest appendRequest = new AppendRequest(value, sequenceNumber);

		AppendMessage message = new AppendMessage(id, Message.Type.APPEND_REQUEST, receiver);

		message.setMessage(new Gson().toJson(appendRequest));
		message.signSelf(String.format("/tmp/priv_%d.key", id));

		return message;
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

	@Test
	public void appendCommands() {
		StringState state = new StringState();

		int receiver = 0;

		int clientId1 = 4;
		int seq1 = 1;
		String value1 = "a";
		AppendMessage proof1 = createAppendRequestMessage(clientId1, receiver, value1, seq1);
		StringCommand cmd1 = new StringCommand(clientId1, seq1, value1, proof1);

		int clientId2 = 4;
		int seq2 = 2;
		String value2 = "b";
		AppendMessage proof2 = createAppendRequestMessage(clientId2, receiver, value2, seq2);
		StringCommand cmd2 = new StringCommand(clientId2, seq2, value2, proof2);

		state.update(cmd1);
		state.update(cmd2);

		List<String> contents = state.getState();
		assertEquals(2, contents.size());
		assertEquals("a", contents.get(0));
		assertEquals("b", contents.get(1));
	}

	@Test
	public void failsToRunInconsistentCommand() {
		StringState state = new StringState();

		int receiver = 0;

		int clientId = 4;
		int seq = 1;
		String value = "a";
		String otherValue = "b";
		AppendMessage proof = createAppendRequestMessage(clientId, receiver, value, seq);

		try {
			StringCommand cmd = new StringCommand(clientId, seq, otherValue, proof);
			state.update(cmd);
			throw new RuntimeException("invalid command creation was allowed");
		} catch (RuntimeException e) { // TODO: change this to a HDSLedgerException
			// ok
		}

		List<String> contents = state.getState();
		assertEquals(0, contents.size());
	}
}

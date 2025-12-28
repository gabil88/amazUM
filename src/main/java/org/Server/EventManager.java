package org.Server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class EventManager {
	private final ReentrantLock lock = new ReentrantLock();

	// ==================== Vendas Simultâneas ====================
	// Subscrições ativas para pares de produtos
	private final List<SimultaneousSubscription> simultaneousSubs = new ArrayList<>();
	// Produtos vendidos no dia atual
	private final Set<Integer> productsSoldToday = new HashSet<>();


	// Flag para indicar se o dia terminou
	private boolean dayEnded = false;



	/**
	 * Bloqueia até que ambos os produtos p1 e p2 tenham sido vendidos hoje.
	 * @param productId1 ID do primeiro produto
	 * @param productId2 ID do segundo produto
	 * @return true se ambos foram vendidos, false se o dia terminou primeiro
	 */
	public boolean waitForSimultaneousSales(int productId1, int productId2) {
		lock.lock();
		try {
			Condition myCondition = lock.newCondition();
			SimultaneousSubscription sub = new SimultaneousSubscription(productId1, productId2, myCondition);
			// Estado inicial: já vendidos?
			if (productsSoldToday.contains(productId1)) sub.sold1 = true;
			if (productsSoldToday.contains(productId2)) sub.sold2 = true;
			if (sub.bothSold()) return true;
			if (dayEnded) return false;
			simultaneousSubs.add(sub);
			try {
				while (!sub.bothSold() && !dayEnded) {
					myCondition.await();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				simultaneousSubs.remove(sub);
				return false;
			}
			simultaneousSubs.remove(sub);
			return sub.bothSold();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Chamado quando uma venda é efetuada. Notifica os subscribers relevantes.
	 * @param productId O ID do produto vendido
	 */
	public void notifySale(int productId) {
		lock.lock();
		try {
			productsSoldToday.add(productId);
			// Notifica apenas subscrições que envolvem este produto
			for (SimultaneousSubscription sub : simultaneousSubs) {
				if (sub.involves(productId)) {
					sub.markSold(productId);
					sub.condition.signal();
				}
			}
		} finally {
			lock.unlock();
		}
	}

	// Nova versão: cada subscrição tem o seu estado de sold1/sold2
	private static class SimultaneousSubscription {
		final int productId1;
		final int productId2;
		final Condition condition;
		volatile boolean sold1 = false;
		volatile boolean sold2 = false;
		SimultaneousSubscription(int p1, int p2, Condition cond) {
			this.productId1 = p1;
			this.productId2 = p2;
			this.condition = cond;
		}
		boolean bothSold() {
			return sold1 && sold2;
		}
		void markSold(int productId) {
			if (productId == productId1) sold1 = true;
			if (productId == productId2) sold2 = true;
		}
		boolean involves(int productId) {
			return productId == productId1 || productId == productId2;
		}
	}
}

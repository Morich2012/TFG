import type { EIP1193Provider } from "viem";

// EIP-6963: descubrimiento de wallets inyectadas sin pelearse por window.ethereum.
export interface ProviderDetail {
  info: { uuid: string; name: string; icon: string; rdns: string };
  provider: EIP1193Provider;
}

export function discoverWallets(timeoutMs = 300): Promise<ProviderDetail[]> {
  return new Promise((resolve) => {
    const found = new Map<string, ProviderDetail>();
    const onAnnounce = (event: Event) => {
      const detail = (event as CustomEvent<ProviderDetail>).detail;
      found.set(detail.info.uuid, detail);
    };
    window.addEventListener("eip6963:announceProvider", onAnnounce);
    window.dispatchEvent(new Event("eip6963:requestProvider"));
    setTimeout(() => {
      window.removeEventListener("eip6963:announceProvider", onAnnounce);
      resolve([...found.values()]);
    }, timeoutMs);
  });
}

export async function findMetaMask(): Promise<ProviderDetail | undefined> {
  const wallets = await discoverWallets();
  return wallets.find((w) => w.info.rdns === "io.metamask" || w.info.rdns === "io.metamask.flask");
}

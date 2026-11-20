import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, type ReactNode } from "react";
import { api, ApiError, ensureCsrf } from "./api";
import type { Me } from "./types";

async function loadMe(): Promise<Me | null> {
  await ensureCsrf();
  try {
    return await api<Me>("/api/v1/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      try {
        return await api<Me>("/api/v1/auth/refresh", { method: "POST" });
      } catch {
        return null;
      }
    }
    throw error;
  }
}

const SessionContext = createContext<{ me: Me | null; loading: boolean; refresh: () => Promise<void> }>({
  me: null,
  loading: true,
  refresh: async () => {},
});

export function SessionProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ["me"], queryFn: loadMe, retry: false });
  return (
    <SessionContext.Provider
      value={{
        me: query.data ?? null,
        loading: query.isLoading,
        refresh: async () => {
          await client.invalidateQueries({ queryKey: ["me"] });
        },
      }}
    >
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  return useContext(SessionContext);
}

export function companyId(me: Me | null) {
  const stored = localStorage.getItem("firstrep-company");
  if (me?.memberships.some((item) => item.companyId === stored)) {
    return stored;
  }
  return me?.memberships[0]?.companyId ?? null;
}

export function hasRole(me: Me | null, role: string) {
  return !!me?.platformRoles.includes(role) || !!me?.memberships.some((item) => item.roles.includes(role));
}

package com.scanmesh.api.common.net;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/** JDK에 들어 있는 JNDI DNS 클라이언트로 TXT 레코드를 조회한다. 서버의 기본 DNS 설정을 쓴다 */
public class JndiDnsTxtLookup implements DnsTxtLookup {

    @Override
    public List<String> lookup(String name) throws IOException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        // 첫 시도 2초, 재시도 1번(4초): 응답 없는 DNS 서버 때문에 요청이 오래 붙잡히지 않게 한다
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attribute txt = context.getAttributes(name, new String[]{"TXT"}).get("TXT");
            List<String> values = new ArrayList<>();
            if (txt == null) {
                return values;
            }
            NamingEnumeration<?> all = txt.getAll();
            while (all.hasMore()) {
                values.add(unquote(String.valueOf(all.next())));
            }
            return values;
        } catch (NameNotFoundException e) {
            return List.of();
        } catch (NamingException e) {
            throw new IOException("DNS 조회 실패: " + name, e);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                    // 조회는 끝났으므로 닫기 실패는 무시한다
                }
            }
        }
    }

    /** 255자를 넘어 여러 조각("abc" "def")으로 나뉜 값을 하나로 붙이고 따옴표를 뗀다 */
    static String unquote(String value) {
        return value.strip().replaceAll("\"\\s+\"", "").replace("\"", "");
    }
}

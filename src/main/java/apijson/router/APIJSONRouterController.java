/*Copyright ©2022 APIJSON(https://github.com/APIJSON)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package apijson.router;

import static apijson.JSON.getJSONObject;
import static apijson.JSON.getString;
import static apijson.RequestMethod.GET;
import static apijson.framework.APIJSONConstant.METHODS;

import java.util.*;
import java.util.Map.Entry;

import jakarta.servlet.http.HttpSession;

import apijson.JSON;
import apijson.JSONRequest;
import apijson.Log;
import apijson.RequestMethod;
import apijson.StringUtil;
import apijson.framework.APIJSONConstant;
import apijson.framework.APIJSONController;
import apijson.framework.APIJSONCreator;
import apijson.framework.APIJSONParser;
import apijson.orm.AbstractVerifier;
import apijson.orm.Parser;
import apijson.orm.SQLConfig;
import apijson.orm.Verifier;


/**APIJSON router controller，建议在子项目被 @RestController 注解的类继承它或通过它的实例调用相关方法
 * @author Lemon
 */
public class APIJSONRouterController<T, M extends Map<String, Object>, L extends List<Object>>
		extends APIJSONController<T, M, L> {
	public static final String TAG = "APIJSONRouterController";

	//通用接口，非事务型操作 和 简单事务型操作 都可通过这些接口自动化实现<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**增删改查统一的类 RESTful API 入口，牺牲一些路由解析性能来提升一点开发效率
	 * compatCommonAPI = Log.DEBUG
	 * @param method
	 * @param tag
	 * @param params
	 * @param request
	 * @param session
	 * @return
	 */
	public String router(String method, String tag, Map<String, String> params, String request, HttpSession session) {
		return router(method, tag, params, request, session, Log.DEBUG);
	}
	/**增删改查统一的类 RESTful API 入口，牺牲一些路由解析性能来提升一点开发效率
	 * @param method
	 * @param tag
	 * @param params
	 * @param request
	 * @param session
	 * @param compatCommonAPI 兼容万能通用 API，当没有映射 APIJSON 格式请求时，自动转到万能通用 API
	 * @return
	 */
	public String router(String method, String tag, Map<String, String> params, String request, HttpSession session, boolean compatCommonAPI) {
		RequestMethod requestMethod = null;
		try {
			 requestMethod = RequestMethod.valueOf(method.toUpperCase());
		} catch (Throwable e) {
			// 下方 METHODS.contains(method) 会抛异常
		}
		Parser<T, M, L> parser = newParser(session, requestMethod);

		if (METHODS.contains(method) == false) {
			return JSON.toJSONString(
					parser.newErrorResult(
							new IllegalArgumentException("URL 路径 /{method}/{tag} 中 method 值 "
									+ method + " 错误！只允许 " + METHODS + " 中的一个！"
							)
					)
			);
		}

		String t = compatCommonAPI && tag != null && tag.endsWith("[]") ? tag.substring(0, tag.length() - 2) : tag;
		if (StringUtil.isName(t) == false) {
			return JSON.toJSONString(
					parser.newErrorResult(
							new IllegalArgumentException("URL 路径 /" + method + "/{tag} 的 tag 中 "
									+ t + " 错误！tag 不能为空，且只允许变量命名格式！"
							)
					)
			);
		}

		String versionStr = params == null ? null : params.remove(APIJSONConstant.VERSION);
		Integer version;
		try {
			version = StringUtil.isEmpty(versionStr, false) ? null : Integer.valueOf(versionStr);
		}
		catch (Exception e) {
			return JSON.toJSONString(
					parser.newErrorResult(new IllegalArgumentException("URL 路径 /" + method + "/"
							+ tag + "?version=value 中 value 值 " + versionStr + " 错误！必须符合整数格式！")
					)
			);
		}

		if (version == null) {
			version = 0;
		}

		try {
			// 从 Document 查这样的接口
			String cacheKey = AbstractVerifier.getCacheKeyForRequest(method, tag);
			SortedMap<Integer, Map<String, Object>> versionedMap = APIJSONRouterVerifier.DOCUMENT_MAP.get(cacheKey);

			Map<String, Object> result = versionedMap == null ? null : versionedMap.get(version);
			if (result == null) {  // version <= 0 时使用最新，version > 0 时使用 > version 的最接近版本（最小版本）
				Set<Entry<Integer, Map<String, Object>>> set = versionedMap == null ? null : versionedMap.entrySet();

				if (set != null && set.isEmpty() == false) {
					Entry<Integer, Map<String, Object>> maxEntry = null;

					for (Entry<Integer, Map<String, Object>> entry : set) {
						if (entry == null || entry.getKey() == null || entry.getValue() == null) {
							continue;
						}

						if (version == null || version <= 0 || version == entry.getKey()) {  // 这里应该不会出现相等，因为上面 versionedMap.get(Integer.valueOf(version))
							maxEntry = entry;
							break;
						}

						if (entry.getKey() < version) {
							break;
						}

						maxEntry = entry;
					}

					result = maxEntry == null ? null : maxEntry.getValue();
				}

				if (result != null) {  // 加快下次查询，查到值的话组合情况其实是有限的，不属于恶意请求
					if (versionedMap == null) {
						versionedMap = new TreeMap<>((o1, o2) -> {
							return o2 == null ? -1 : o2.compareTo(o1);  // 降序
						});
					}

					versionedMap.put(version, result);
					APIJSONRouterVerifier.DOCUMENT_MAP.put(cacheKey, versionedMap);
				}
			}

			@SuppressWarnings("unchecked")
			APIJSONCreator<T, M, L> creator = (APIJSONCreator<T, M, L>) APIJSONParser.APIJSON_CREATOR;
			if (result == null && Log.DEBUG && APIJSONRouterVerifier.DOCUMENT_MAP.isEmpty()) {

				//获取指定的JSON结构 <<<<<<<<<<<<<<
				SQLConfig<T, M, L> config = creator.createSQLConfig().setMethod(GET).setTable(APIJSONConstant.DOCUMENT_);
				config.setPrepared(false);
				config.setColumn(Arrays.asList("request,apijson"));

				Map<String, Object> where = new HashMap<String, Object>();
				where.put("url", "/" + method + "/" + tag);
				where.put("apijson{}", "length(apijson)>0");

				if (version > 0) {
					where.put(JSONRequest.KEY_VERSION + ">=", version);
				}
				config.setWhere(where);
				config.setOrder(JSONRequest.KEY_VERSION + (version > 0 ? "+" : "-"));
				config.setCount(1);

				//too many connections error: 不try-catch，可以让客户端看到是服务器内部异常
				result = creator.createSQLExecutor().execute(config, false);

				// version, method, tag 组合情况太多了，JDK 里又没有 LRUCache，所以要么启动时一次性缓存全部后面只用缓存，要么每次都查数据库
				//			versionedMap.put(Integer.valueOf(version), result);
				//			DOCUMENT_MAP.put(cacheKey, versionedMap);
			}

			String apijson = result == null ? null : getString(result, "apijson");
			if (StringUtil.isEmpty(apijson, true)) {  //
				if (compatCommonAPI) {
					return crudByTag(method, tag, params, request, session);
				}

				throw new IllegalArgumentException("URL 路径 /" + method
						+ "/" + tag + (versionStr == null ? "" : "?version=" + versionStr) + " 对应的接口不存在！");
			}

			M rawReq = JSON.parseObject(request);
			if (rawReq == null) {
				rawReq = JSON.createJSONObject();
			}
			if (params != null && params.isEmpty() == false) {
				rawReq.putAll(params);
			}

			if (parser.isNeedVerifyContent()) {
				Verifier<T, M, L> verifier = creator.createVerifier();

				//获取指定的JSON结构 <<<<<<<<<<<<
				Map<String, Object> target = parser.getStructure("Request", method.toUpperCase(), tag, version);
				if (target == null) { //empty表示随意操作  || object.isEmpty()) {
					throw new UnsupportedOperationException("找不到 version: " + version + ", method: " + method.toUpperCase() + ", tag: " + tag + " 对应的 structure ！"
							+ "非开放请求必须是后端 Request 表中校验规则允许的操作！如果需要则在 Request 表中新增配置！");
				}

				//M clone 浅拷贝没用，Structure.parse 会导致 structure 里面被清空，第二次从缓存里取到的就是 {}
				verifier.verifyRequest(requestMethod, "", JSON.createJSONObject(target), rawReq, 0, null, null, creator);
			}

			M apijsonReq = JSON.parseObject(apijson);
			if (apijsonReq == null) {
				apijsonReq = JSON.createJSONObject();
			}

			Set<Entry<String, Object>> rawSet = rawReq.entrySet();
			if (rawSet != null && rawSet.isEmpty() == false) {
				for (Entry<String, Object> entry : rawSet) {
					String key = entry == null ? null : entry.getKey();
					if (key == null) {  // value 为 null 有效
						continue;
					}

					String[] pathKeys = key.split("\\.");
					//逐层到达child的直接容器JSONObject parent
					int last = pathKeys.length - 1;
					M parent = apijsonReq;
					for (int i = 0; i < last; i++) {//一步一步到达指定位置
						M p = getJSONObject(parent, pathKeys[i]);
						if (p == null) {
							p = JSON.createJSONObject();
							parent.put(key, p);
						}
						parent = p;
					}

					parent.put(pathKeys[last], entry.getValue());
				}
			}

			// 没必要，已经是预设好的实际参数了，如果要 tag 就在 apijson 字段配置  apijsonReq.put(JSONRequest.KEY_TAG, tag);

			return parser.setNeedVerifyContent(false).parse(apijsonReq);
		}
		catch (Exception e) {
			return JSON.toJSONString(parser.newErrorResult(e));
		}
	}


}

package com.example.onlineshop.catalog.persistence;

import com.example.onlineshop.catalog.application.CatalogReadPort;
import com.example.onlineshop.catalog.application.InvalidCatalogQueryException;
import static com.example.onlineshop.catalog.application.CatalogReadPort.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresCatalogReadAdapter implements CatalogReadPort {
  private final JdbcTemplate jdbc;
  public PostgresCatalogReadAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}

  private static final String VISIBLE="""
    FROM product p JOIN product_sku d ON d.product_id=p.id AND d.is_default AND d.status='ACTIVE'
    WHERE p.status='ACTIVE'""";

  @Override public ProductPage browse(CatalogQuery q) {
    validate(q);
    var args=new ArrayList<Object>(); var where=new StringBuilder(VISIBLE);
    if(!q.categories().isEmpty()) { where.append(" AND EXISTS (SELECT 1 FROM product_category pc JOIN category c ON c.id=pc.category_id WHERE pc.product_id=p.id AND c.status='ACTIVE' AND lower(c.slug) IN ("); placeholders(where,args,q.categories().stream().map(String::toLowerCase).toList()); where.append("))"); }
    for(var entry:q.attributes().entrySet()) { where.append(" AND EXISTS (SELECT 1 FROM product_attribute a JOIN product_attribute_value av ON av.attribute_id=a.id LEFT JOIN product_attribute_assignment paa ON paa.attribute_id=a.id AND paa.value_id=av.id LEFT JOIN product_sku_attribute_value sav ON sav.attribute_id=a.id AND sav.value_id=av.id LEFT JOIN product_sku vs ON vs.id=sav.product_sku_id AND vs.product_id=p.id AND vs.status='ACTIVE' WHERE a.status='ACTIVE' AND a.is_filterable AND lower(a.code)=? AND av.id IN ("); args.add(entry.getKey().toLowerCase()); placeholders(where,args,entry.getValue()); where.append(") AND (paa.product_id=p.id OR vs.id IS NOT NULL))"); }
    if(!"any".equals(q.availability())) where.append(" AND ").append("in-stock".equals(q.availability())?"EXISTS":"NOT EXISTS").append(" (SELECT 1 FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.available_quantity>0)");
    if(q.currency()!=null) { where.append(" AND d.currency=?"); args.add(q.currency()); }
    if(q.minPrice()!=null) { where.append(" AND (SELECT min(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.currency=?)>=?");args.add(q.currency());args.add(q.minPrice()); }
    if(q.maxPrice()!=null) { where.append(" AND (SELECT min(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.currency=?)<=?");args.add(q.currency());args.add(q.maxPrice()); }
    long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,args.toArray());
    String priceOrder="(SELECT min(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE'"+(q.currency()!=null?" AND s.currency=?":"")+")";
    String order=switch(q.sort()){case "name-asc"->"lower(p.name),p.id";case "price-asc"->priceOrder+" ASC NULLS LAST,p.id";case "price-desc"->priceOrder+" DESC NULLS LAST,p.id";default->"p.updated_at DESC,p.id";};
    long offset=Math.multiplyExact((long)q.page(),q.size()); var pageArgs=new ArrayList<>(args);if(q.currency()!=null&&q.sort().startsWith("price-"))pageArgs.add(q.currency());pageArgs.add(q.size());pageArgs.add(offset);
    var ids=jdbc.query("SELECT p.id "+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",(rs,n)->rs.getObject(1,UUID.class),pageArgs.toArray());
    var items=summaries(ids); int pages=total==0?0:(int)Math.ceil((double)total/q.size());
    return new ProductPage(items,new PageMetadata(q.page(),q.size(),items.size(),total,pages,q.page()==0,pages==0||q.page()>=pages-1),facets(where.toString(),args,q));
  }

  private void validate(CatalogQuery q){var errors=new ArrayList<InvalidCatalogQueryException.Violation>();for(String slug:q.categories())if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM category WHERE status='ACTIVE' AND lower(slug)=lower(?))",Boolean.class,slug)))errors.add(new InvalidCatalogQueryException.Violation("category","unknown active category: "+slug));for(var e:q.attributes().entrySet()){var definition=jdbc.query("SELECT id FROM product_attribute WHERE status='ACTIVE' AND is_filterable AND lower(code)=lower(?)",(r,n)->r.getObject(1,UUID.class),e.getKey());if(definition.isEmpty()){errors.add(new InvalidCatalogQueryException.Violation("attribute","unknown active filterable attribute: "+e.getKey()));continue;}for(UUID value:e.getValue())if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM product_attribute_value WHERE attribute_id=? AND id=?)",Boolean.class,definition.get(0),value)))errors.add(new InvalidCatalogQueryException.Violation("attribute","unknown value for "+e.getKey()+": "+value));}if(!errors.isEmpty())throw new InvalidCatalogQueryException(errors);}

  @Override public Optional<ProductDetail> findById(UUID id){return detail("p.id=?",id);}
  @Override public Optional<ProductDetail> findBySlug(String slug){return detail("lower(p.slug)=lower(?)",slug);}
  private Optional<ProductDetail> detail(String condition,Object value){
    var ids=jdbc.query("SELECT p.id "+VISIBLE+" AND "+condition,(rs,n)->rs.getObject(1,UUID.class),value);
    if(ids.isEmpty()) return Optional.empty(); UUID id=ids.get(0);ProductSummary s=summaries(ids).get(0);
    var times=jdbc.queryForMap("SELECT created_at FROM product WHERE id=?",id);
    var skus=skus(id); var images=images(id); var attrs=attributes("paa.product_id=?",id);
    var axes=variantAxes(id);
    return Optional.of(new ProductDetail(s.id(),s.slug(),s.name(),s.description(),s.defaultSkuId(),s.price(),s.priceRange(),s.availability(),s.primaryImage(),s.categories(),((java.sql.Timestamp)times.get("created_at")).toInstant(),s.updatedAt(),attrs,axes,skus,images));
  }
  private List<ProductSummary> summaries(List<UUID> ids){
    if(ids.isEmpty())return List.of();var imageMap=images(ids);var categoryMap=categories(ids);var args=new ArrayList<Object>();var in=new StringBuilder();placeholders(in,args,ids);var byId=new HashMap<UUID,ProductSummary>();
    jdbc.query("""
      SELECT p.*,d.id default_id,d.price_amount,d.currency,
       (SELECT min(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.currency=d.currency) min_price,
       (SELECT max(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.currency=d.currency) max_price,
       EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.available_quantity>0) stocked
      FROM product p JOIN product_sku d ON d.product_id=p.id AND d.is_default AND d.status='ACTIVE' WHERE p.id IN ("""+in+") AND p.status='ACTIVE'",rs->{UUID id=rs.getObject("id",UUID.class);var imgs=imageMap.getOrDefault(id,List.of());var categoryRefs=categoryMap.getOrDefault(id,List.of());
      String currency=rs.getString("currency"); Money price=money(rs.getBigDecimal("price_amount"),currency);
      Image primary=imgs.stream().filter(x->x.skuId()==null&&x.primary()).findFirst().orElse(null);
      byId.put(id,new ProductSummary(id,rs.getString("slug"),rs.getString("name"),rs.getString("description"),rs.getObject("default_id",UUID.class),price,new PriceRange(money(rs.getBigDecimal("min_price"),currency),money(rs.getBigDecimal("max_price"),currency)),rs.getBoolean("stocked")?"IN_STOCK":"OUT_OF_STOCK",primary,categoryRefs,rs.getTimestamp("updated_at").toInstant()));},args.toArray());return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
  }
  private Map<UUID,List<CategoryRef>> categories(List<UUID> ids){if(ids.isEmpty())return Map.of();var sql=new StringBuilder();var args=new ArrayList<Object>();placeholders(sql,args,ids);var out=new LinkedHashMap<UUID,List<CategoryRef>>();jdbc.query("SELECT pc.product_id,c.*,CASE WHEN parent.status='ACTIVE' THEN c.parent_id END visible_parent FROM product_category pc JOIN category c ON c.id=pc.category_id LEFT JOIN category parent ON parent.id=c.parent_id WHERE pc.product_id IN ("+sql+") AND c.status='ACTIVE' ORDER BY pc.product_id,c.display_order,lower(c.name),c.id",rs->{UUID id=rs.getObject("product_id",UUID.class);out.computeIfAbsent(id,x->new ArrayList<>()).add(new CategoryRef(rs.getObject("id",UUID.class),rs.getString("slug"),rs.getString("name"),rs.getObject("visible_parent",UUID.class),rs.getInt("display_order")));},args.toArray());return out;}
  private Map<UUID,List<Image>> images(List<UUID> ids){if(ids.isEmpty())return Map.of();var sql=new StringBuilder();var args=new ArrayList<Object>();placeholders(sql,args,ids);var out=new LinkedHashMap<UUID,List<Image>>();jdbc.query("SELECT i.* FROM product_image i LEFT JOIN product_sku s ON s.id=i.product_sku_id WHERE i.product_id IN ("+sql+") AND i.width_px IS NOT NULL AND i.height_px IS NOT NULL AND (i.product_sku_id IS NULL OR s.status='ACTIVE') ORDER BY i.product_id,CASE WHEN i.product_sku_id IS NULL THEN 0 ELSE 1 END,i.display_order,i.id",rs->{UUID id=rs.getObject("product_id",UUID.class);out.computeIfAbsent(id,x->new ArrayList<>()).add(new Image(rs.getObject("id",UUID.class),rs.getObject("product_sku_id",UUID.class),rs.getString("url"),rs.getString("alt_text"),rs.getInt("width_px"),rs.getInt("height_px"),rs.getInt("display_order"),rs.getBoolean("is_primary")));},args.toArray());return out;}
  private List<CategoryRef> categories(UUID id){return jdbc.query("""
    SELECT c.*,CASE WHEN parent.status='ACTIVE' THEN c.parent_id END visible_parent FROM product_category pc JOIN category c ON c.id=pc.category_id LEFT JOIN category parent ON parent.id=c.parent_id WHERE pc.product_id=? AND c.status='ACTIVE' ORDER BY c.display_order,lower(c.name),c.id""",(r,n)->new CategoryRef(r.getObject("id",UUID.class),r.getString("slug"),r.getString("name"),r.getObject("visible_parent",UUID.class),r.getInt("display_order")),id);}
  private List<Image> images(UUID id){return jdbc.query("""
    SELECT i.* FROM product_image i LEFT JOIN product_sku s ON s.id=i.product_sku_id WHERE i.product_id=? AND i.width_px IS NOT NULL AND i.height_px IS NOT NULL AND (i.product_sku_id IS NULL OR s.status='ACTIVE') ORDER BY CASE WHEN i.product_sku_id IS NULL THEN 0 ELSE 1 END,i.display_order,i.id""",(r,n)->new Image(r.getObject("id",UUID.class),r.getObject("product_sku_id",UUID.class),r.getString("url"),r.getString("alt_text"),r.getInt("width_px"),r.getInt("height_px"),r.getInt("display_order"),r.getBoolean("is_primary")),id);}
  private List<Sku> skus(UUID id){var images=images(id);var rows=jdbc.query("SELECT * FROM product_sku WHERE product_id=? AND status='ACTIVE' ORDER BY is_default DESC,upper(sku),id",(r,n)->new Object[]{r.getObject("id",UUID.class),r.getString("sku"),r.getBoolean("is_default"),r.getBigDecimal("price_amount"),r.getString("currency"),r.getInt("available_quantity")},id);var attrs=attributesBySku(rows.stream().map(x->(UUID)x[0]).toList());return rows.stream().map(r->{UUID sid=(UUID)r[0];int qty=(Integer)r[5];return new Sku(sid,(String)r[1],(Boolean)r[2],money((BigDecimal)r[3],(String)r[4]),qty,qty>0?"IN_STOCK":"OUT_OF_STOCK",attrs.getOrDefault(sid,List.of()),images.stream().filter(i->sid.equals(i.skuId())).map(Image::id).toList());}).toList();}
  private Map<UUID,List<AttributeSelection>> attributesBySku(List<UUID> ids){if(ids.isEmpty())return Map.of();var in=new StringBuilder();var args=new ArrayList<Object>();placeholders(in,args,ids);var rows=jdbc.query("""
    SELECT sav.product_sku_id owner,a.id aid,a.code,a.name,a.data_type,a.scope,a.is_variant_axis,av.id vid,av.text_value,av.integer_value,av.decimal_value,av.boolean_value,av.display_value
    FROM product_attribute a JOIN product_attribute_value av ON av.attribute_id=a.id JOIN product_sku_attribute_value sav ON sav.attribute_id=a.id AND sav.value_id=av.id
    WHERE a.status='ACTIVE' AND sav.product_sku_id IN ("""+in+") ORDER BY sav.product_sku_id,a.display_order,a.code,av.display_order,av.id",(r,n)->new Object[]{r.getObject("owner",UUID.class),r.getObject("aid",UUID.class),r.getString("code"),r.getString("name"),r.getString("data_type"),r.getString("scope"),r.getBoolean("is_variant_axis"),r.getObject("vid",UUID.class),attributeValue(r),Optional.ofNullable(r.getString("display_value"))},args.toArray());var out=new LinkedHashMap<UUID,List<AttributeSelection>>();for(UUID owner:ids){var selections=new ArrayList<AttributeSelection>();var grouped=new LinkedHashMap<UUID,List<Object[]>>();rows.stream().filter(x->owner.equals(x[0])).forEach(x->grouped.computeIfAbsent((UUID)x[1],k->new ArrayList<>()).add(x));for(var group:grouped.values()){var x=group.get(0);var def=new AttributeDefinition((UUID)x[1],(String)x[2],(String)x[3],(String)x[4],(String)x[5],(Boolean)x[6]);var values=group.stream().map(v->new AttributeValue((UUID)v[7],v[8],((Optional<String>)v[9]).orElse(String.valueOf(v[8])))).toList();selections.add(new AttributeSelection(def,values));}out.put(owner,selections);}return out;}
  private static Object attributeValue(ResultSet r)throws SQLException{return switch(r.getString("data_type")){case "INTEGER"->r.getLong("integer_value");case "DECIMAL"->r.getBigDecimal("decimal_value").stripTrailingZeros().toPlainString();case "BOOLEAN"->r.getBoolean("boolean_value");default->r.getString("text_value");};}
  private List<AttributeSelection> attributes(String condition,Object value){return jdbc.query("""
    SELECT DISTINCT a.id aid,a.code,a.name,a.data_type,a.scope,a.is_variant_axis,av.id vid,av.text_value,av.integer_value,av.decimal_value,av.boolean_value,av.display_value,a.display_order,av.display_order
    FROM product_attribute a JOIN product_attribute_value av ON av.attribute_id=a.id LEFT JOIN product_attribute_assignment paa ON paa.attribute_id=a.id AND paa.value_id=av.id LEFT JOIN product_sku_attribute_value sav ON sav.attribute_id=a.id AND sav.value_id=av.id
    WHERE a.status='ACTIVE' AND """+" "+condition+" ORDER BY a.display_order,a.code,av.display_order,av.id",(org.springframework.jdbc.core.ResultSetExtractor<List<AttributeSelection>>)this::groupAttributes,value);}
  private List<AttributeSelection> groupAttributes(ResultSet rs)throws SQLException{var out=new ArrayList<AttributeSelection>();AttributeDefinition def=null;var vals=new ArrayList<AttributeValue>();while(rs.next()){UUID aid=rs.getObject("aid",UUID.class);if(def==null||!def.id().equals(aid)){if(def!=null)out.add(new AttributeSelection(def,List.copyOf(vals)));def=new AttributeDefinition(aid,rs.getString("code"),rs.getString("name"),rs.getString("data_type"),rs.getString("scope"),rs.getBoolean("is_variant_axis"));vals.clear();}Object val=switch(rs.getString("data_type")){case "INTEGER"->rs.getLong("integer_value");case "DECIMAL"->rs.getBigDecimal("decimal_value").stripTrailingZeros().toPlainString();case "BOOLEAN"->rs.getBoolean("boolean_value");default->rs.getString("text_value");};vals.add(new AttributeValue(rs.getObject("vid",UUID.class),val,Optional.ofNullable(rs.getString("display_value")).orElse(String.valueOf(val))));}if(def!=null)out.add(new AttributeSelection(def,List.copyOf(vals)));return out;}
  private List<VariantAxis> variantAxes(UUID product){var selections=attributes("sav.product_sku_id IN (SELECT id FROM product_sku WHERE product_id=? AND status='ACTIVE') AND a.is_variant_axis",product);var stocked=new HashSet<UUID>(jdbc.query("SELECT DISTINCT x.value_id FROM product_sku_attribute_value x JOIN product_sku ps ON ps.id=x.product_sku_id WHERE ps.product_id=? AND ps.status='ACTIVE' AND ps.available_quantity>0",(r,n)->r.getObject(1,UUID.class),product));return selections.stream().map(s->new VariantAxis(s.definition(),s.values().stream().map(v->new VariantOption(v.id(),v.value(),v.displayValue(),stocked.contains(v.id())?"IN_STOCK":"OUT_OF_STOCK")).toList())).toList();}
  private Facets facets(String where,List<Object> args,CatalogQuery q){
    var cats=jdbc.query("SELECT c.id,c.slug,count(DISTINCT p.id) n FROM product p JOIN product_sku d ON d.product_id=p.id AND d.is_default AND d.status='ACTIVE' JOIN product_category pc ON pc.product_id=p.id JOIN category c ON c.id=pc.category_id AND c.status='ACTIVE' WHERE p.id IN (SELECT p.id "+where+") GROUP BY c.id,c.slug,c.display_order,c.name ORDER BY c.display_order,c.name,c.id LIMIT 201",(r,n)->{String slug=r.getString(2);return new FacetBucket(r.getObject(1,UUID.class).toString(),slug,q.categories().stream().anyMatch(x->x.equalsIgnoreCase(slug)),r.getLong(3));},args.toArray());
    var avail=jdbc.query("SELECT CASE WHEN EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.available_quantity>0) THEN 'IN_STOCK' ELSE 'OUT_OF_STOCK' END v,count(*) FROM product p WHERE p.id IN (SELECT p.id "+where+") GROUP BY v ORDER BY v",(r,n)->new FacetBucket(r.getString(1),r.getString(1),q.availability().replace('-','_').equalsIgnoreCase(r.getString(1)),r.getLong(2)),args.toArray());
    PriceFacet price=null;var prices=jdbc.query("SELECT min(s.price_amount),max(s.price_amount),CASE WHEN count(DISTINCT s.currency)=1 THEN min(s.currency) END FROM product_sku s WHERE s.status='ACTIVE' AND s.product_id IN (SELECT p.id "+where+")"+(q.currency()!=null?" AND s.currency=?":""),(r,n)->new Object[]{r.getBigDecimal(1),r.getBigDecimal(2),r.getString(3)},append(args,q.currency()).toArray());if(!prices.isEmpty()&&prices.get(0)[2]!=null)price=new PriceFacet(fmt((BigDecimal)prices.get(0)[0]),fmt((BigDecimal)prices.get(0)[1]),(String)prices.get(0)[2]);
    int categoryCount=Math.min(cats.size(),200),remaining=200-categoryCount;
    var rows=jdbc.query("""
      SELECT a.code,a.name,av.id,COALESCE(av.display_value,av.text_value,av.integer_value::text,av.decimal_value::text,av.boolean_value::text),count(DISTINCT p.id),a.display_order,av.display_order
      FROM product p JOIN product_sku d ON d.product_id=p.id AND d.is_default AND d.status='ACTIVE'
      JOIN (SELECT product_id,attribute_id,value_id FROM product_attribute_assignment UNION ALL SELECT s.product_id,x.attribute_id,x.value_id FROM product_sku_attribute_value x JOIN product_sku s ON s.id=x.product_sku_id AND s.status='ACTIVE') assigned ON assigned.product_id=p.id
      JOIN product_attribute a ON a.id=assigned.attribute_id AND a.status='ACTIVE' AND a.is_filterable
      JOIN product_attribute_value av ON av.id=assigned.value_id
      WHERE p.id IN (SELECT p.id """+" "+where+") GROUP BY a.code,a.name,av.id,a.display_order,av.display_order,av.display_value,av.text_value,av.integer_value,av.decimal_value,av.boolean_value ORDER BY a.display_order,a.code,av.display_order,4,av.id LIMIT "+(remaining+1),(r,n)->new Object[]{r.getString(1),r.getString(2),r.getObject(3,UUID.class),r.getString(4),r.getLong(5)},args.toArray());
    boolean truncated=cats.size()>200||rows.size()>remaining;var selectedByCode=new TreeMap<String,List<UUID>>(String.CASE_INSENSITIVE_ORDER);selectedByCode.putAll(q.attributes());var grouped=new LinkedHashMap<String,AttributeFacet>();for(Object[] row:rows.stream().limit(remaining).toList()){String code=(String)row[0];var facet=grouped.computeIfAbsent(code,k->new AttributeFacet(code,(String)row[1],new ArrayList<>()));@SuppressWarnings("unchecked") var values=(List<FacetBucket>)facet.values();UUID value=(UUID)row[2];boolean selected=selectedByCode.getOrDefault(code,List.of()).contains(value);values.add(new FacetBucket(value.toString(),(String)row[3],selected,(Long)row[4]));}
    var attributes=grouped.values().stream().map(f->new AttributeFacet(f.code(),f.name(),List.copyOf(f.values()))).toList();return new Facets(cats.stream().limit(200).toList(),attributes,avail,price,truncated);
  }
  private static List<Object> append(List<Object>a,Object x){var n=new ArrayList<>(a);if(x!=null)n.add(x);return n;}
  private static void placeholders(StringBuilder s,List<Object>a,Collection<?> v){int i=0;for(Object x:v){if(i++>0)s.append(',');s.append('?');a.add(x);}}
  private static Money money(BigDecimal n,String c){return new Money(fmt(n),c);}
  private static String fmt(BigDecimal n){return n==null?null:n.setScale(2).toPlainString();}
}

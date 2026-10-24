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
    String order=switch(q.sort()){case "name-asc"->"lower(p.name),p.id";case "price-asc"->"(SELECT min(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE'"+(q.currency()!=null?" AND s.currency='"+q.currency()+"'":"")+") ASC NULLS LAST,p.id";case "price-desc"->"(SELECT min(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE'"+(q.currency()!=null?" AND s.currency='"+q.currency()+"'":"")+") DESC NULLS LAST,p.id";default->"p.updated_at DESC,p.id";};
    long offset=Math.multiplyExact((long)q.page(),q.size()); var pageArgs=new ArrayList<>(args);pageArgs.add(q.size());pageArgs.add(offset);
    var ids=jdbc.query("SELECT p.id "+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",(rs,n)->rs.getObject(1,UUID.class),pageArgs.toArray());
    var items=ids.stream().map(this::summary).toList(); int pages=total==0?0:(int)Math.ceil((double)total/q.size());
    return new ProductPage(items,new PageMetadata(q.page(),q.size(),items.size(),total,pages,q.page()==0,pages==0||q.page()>=pages-1),facets(where.toString(),args,q));
  }

  private void validate(CatalogQuery q){var errors=new ArrayList<InvalidCatalogQueryException.Violation>();for(String slug:q.categories())if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM category WHERE status='ACTIVE' AND lower(slug)=lower(?))",Boolean.class,slug)))errors.add(new InvalidCatalogQueryException.Violation("category","unknown active category: "+slug));for(var e:q.attributes().entrySet()){var definition=jdbc.query("SELECT id FROM product_attribute WHERE status='ACTIVE' AND is_filterable AND lower(code)=lower(?)",(r,n)->r.getObject(1,UUID.class),e.getKey());if(definition.isEmpty()){errors.add(new InvalidCatalogQueryException.Violation("attribute","unknown active filterable attribute: "+e.getKey()));continue;}for(UUID value:e.getValue())if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM product_attribute_value WHERE attribute_id=? AND id=?)",Boolean.class,definition.get(0),value)))errors.add(new InvalidCatalogQueryException.Violation("attribute","unknown value for "+e.getKey()+": "+value));}if(!errors.isEmpty())throw new InvalidCatalogQueryException(errors);}

  @Override public Optional<ProductDetail> findById(UUID id){return detail("p.id=?",id);}
  @Override public Optional<ProductDetail> findBySlug(String slug){return detail("lower(p.slug)=lower(?)",slug);}
  private Optional<ProductDetail> detail(String condition,Object value){
    var ids=jdbc.query("SELECT p.id "+VISIBLE+" AND "+condition,(rs,n)->rs.getObject(1,UUID.class),value);
    if(ids.isEmpty()) return Optional.empty(); UUID id=ids.get(0); ProductSummary s=summary(id);
    var times=jdbc.queryForMap("SELECT created_at FROM product WHERE id=?",id);
    var skus=skus(id); var images=images(id); var attrs=attributes("paa.product_id=?",id);
    var axes=variantAxes(id);
    return Optional.of(new ProductDetail(s.id(),s.slug(),s.name(),s.description(),s.defaultSkuId(),s.price(),s.priceRange(),s.availability(),s.primaryImage(),s.categories(),((java.sql.Timestamp)times.get("created_at")).toInstant(),s.updatedAt(),attrs,axes,skus,images));
  }
  private ProductSummary summary(UUID id){
    return jdbc.queryForObject("""
      SELECT p.*,d.id default_id,d.price_amount,d.currency,
       (SELECT min(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.currency=d.currency) min_price,
       (SELECT max(s.price_amount) FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.currency=d.currency) max_price,
       EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.available_quantity>0) stocked
      FROM product p JOIN product_sku d ON d.product_id=p.id AND d.is_default AND d.status='ACTIVE' WHERE p.id=? AND p.status='ACTIVE'""",(rs,n)->{
      String currency=rs.getString("currency"); Money price=money(rs.getBigDecimal("price_amount"),currency);
      var imgs=images(id); Image primary=imgs.stream().filter(x->x.skuId()==null&&x.primary()).findFirst().orElse(null);
      return new ProductSummary(id,rs.getString("slug"),rs.getString("name"),rs.getString("description"),rs.getObject("default_id",UUID.class),price,new PriceRange(money(rs.getBigDecimal("min_price"),currency),money(rs.getBigDecimal("max_price"),currency)),rs.getBoolean("stocked")?"IN_STOCK":"OUT_OF_STOCK",primary,categories(id),rs.getTimestamp("updated_at").toInstant());},id);
  }
  private List<CategoryRef> categories(UUID id){return jdbc.query("""
    SELECT c.*,CASE WHEN parent.status='ACTIVE' THEN c.parent_id END visible_parent FROM product_category pc JOIN category c ON c.id=pc.category_id LEFT JOIN category parent ON parent.id=c.parent_id WHERE pc.product_id=? AND c.status='ACTIVE' ORDER BY c.display_order,lower(c.name),c.id""",(r,n)->new CategoryRef(r.getObject("id",UUID.class),r.getString("slug"),r.getString("name"),r.getObject("visible_parent",UUID.class),r.getInt("display_order")),id);}
  private List<Image> images(UUID id){return jdbc.query("""
    SELECT i.* FROM product_image i LEFT JOIN product_sku s ON s.id=i.product_sku_id WHERE i.product_id=? AND i.width_px IS NOT NULL AND i.height_px IS NOT NULL AND (i.product_sku_id IS NULL OR s.status='ACTIVE') ORDER BY CASE WHEN i.product_sku_id IS NULL THEN 0 ELSE 1 END,i.display_order,i.id""",(r,n)->new Image(r.getObject("id",UUID.class),r.getObject("product_sku_id",UUID.class),r.getString("url"),r.getString("alt_text"),r.getInt("width_px"),r.getInt("height_px"),r.getInt("display_order"),r.getBoolean("is_primary")),id);}
  private List<Sku> skus(UUID id){var images=images(id);return jdbc.query("SELECT * FROM product_sku WHERE product_id=? AND status='ACTIVE' ORDER BY is_default DESC,upper(sku),id",(r,n)->{UUID sid=r.getObject("id",UUID.class);int qty=r.getInt("available_quantity");return new Sku(sid,r.getString("sku"),r.getBoolean("is_default"),money(r.getBigDecimal("price_amount"),r.getString("currency")),qty,qty>0?"IN_STOCK":"OUT_OF_STOCK",attributes("sav.product_sku_id=?",sid),images.stream().filter(i->sid.equals(i.skuId())).map(Image::id).toList());},id);}
  private List<AttributeSelection> attributes(String condition,Object value){return jdbc.query("""
    SELECT a.id aid,a.code,a.name,a.data_type,a.scope,a.is_variant_axis,av.id vid,av.text_value,av.integer_value,av.decimal_value,av.boolean_value,av.display_value
    FROM product_attribute a JOIN product_attribute_value av ON av.attribute_id=a.id LEFT JOIN product_attribute_assignment paa ON paa.attribute_id=a.id AND paa.value_id=av.id LEFT JOIN product_sku_attribute_value sav ON sav.attribute_id=a.id AND sav.value_id=av.id
    WHERE a.status='ACTIVE' AND """+" "+condition+" ORDER BY a.display_order,a.code,av.display_order,av.id",(org.springframework.jdbc.core.ResultSetExtractor<List<AttributeSelection>>)this::groupAttributes,value);}
  private List<AttributeSelection> groupAttributes(ResultSet rs)throws SQLException{var out=new ArrayList<AttributeSelection>();AttributeDefinition def=null;var vals=new ArrayList<AttributeValue>();while(rs.next()){UUID aid=rs.getObject("aid",UUID.class);if(def==null||!def.id().equals(aid)){if(def!=null)out.add(new AttributeSelection(def,List.copyOf(vals)));def=new AttributeDefinition(aid,rs.getString("code"),rs.getString("name"),rs.getString("data_type"),rs.getString("scope"),rs.getBoolean("is_variant_axis"));vals.clear();}Object val=switch(rs.getString("data_type")){case "INTEGER"->rs.getLong("integer_value");case "DECIMAL"->rs.getBigDecimal("decimal_value").stripTrailingZeros().toPlainString();case "BOOLEAN"->rs.getBoolean("boolean_value");default->rs.getString("text_value");};vals.add(new AttributeValue(rs.getObject("vid",UUID.class),val,Optional.ofNullable(rs.getString("display_value")).orElse(String.valueOf(val))));}if(def!=null)out.add(new AttributeSelection(def,List.copyOf(vals)));return out;}
  private List<VariantAxis> variantAxes(UUID product){var selections=attributes("sav.product_sku_id IN (SELECT id FROM product_sku WHERE product_id=? AND status='ACTIVE') AND a.is_variant_axis",product);return selections.stream().map(s->new VariantAxis(s.definition(),s.values().stream().map(v->new VariantOption(v.id(),v.value(),v.displayValue(),jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM product_sku_attribute_value x JOIN product_sku ps ON ps.id=x.product_sku_id WHERE ps.product_id=? AND ps.status='ACTIVE' AND ps.available_quantity>0 AND x.value_id=?)",Boolean.class,product,v.id())?"IN_STOCK":"OUT_OF_STOCK")).toList())).toList();}
  private Facets facets(String where,List<Object> args,CatalogQuery q){
    var cats=jdbc.query("SELECT c.id,c.slug,count(DISTINCT p.id) n FROM product p JOIN product_sku d ON d.product_id=p.id AND d.is_default AND d.status='ACTIVE' JOIN product_category pc ON pc.product_id=p.id JOIN category c ON c.id=pc.category_id AND c.status='ACTIVE' WHERE p.id IN (SELECT p.id "+where+") GROUP BY c.id,c.slug,c.display_order,c.name ORDER BY c.display_order,c.name,c.id LIMIT 201",(r,n)->{String slug=r.getString(2);return new FacetBucket(r.getObject(1,UUID.class).toString(),slug,q.categories().stream().anyMatch(x->x.equalsIgnoreCase(slug)),r.getLong(3));},args.toArray());
    var avail=jdbc.query("SELECT CASE WHEN EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.id AND s.status='ACTIVE' AND s.available_quantity>0) THEN 'IN_STOCK' ELSE 'OUT_OF_STOCK' END v,count(*) FROM product p WHERE p.id IN (SELECT p.id "+where+") GROUP BY v ORDER BY v",(r,n)->new FacetBucket(r.getString(1),r.getString(1),q.availability().replace('-','_').equalsIgnoreCase(r.getString(1)),r.getLong(2)),args.toArray());
    PriceFacet price=null;var prices=jdbc.query("SELECT min(s.price_amount),max(s.price_amount),CASE WHEN count(DISTINCT s.currency)=1 THEN min(s.currency) END FROM product_sku s WHERE s.status='ACTIVE' AND s.product_id IN (SELECT p.id "+where+")"+(q.currency()!=null?" AND s.currency=?":""),(r,n)->new Object[]{r.getBigDecimal(1),r.getBigDecimal(2),r.getString(3)},append(args,q.currency()).toArray());if(!prices.isEmpty()&&prices.get(0)[2]!=null)price=new PriceFacet(fmt((BigDecimal)prices.get(0)[0]),fmt((BigDecimal)prices.get(0)[1]),(String)prices.get(0)[2]);
    int categoryCount=Math.min(cats.size(),200),remaining=200-categoryCount;
    var rows=remaining==0?List.<Object[]>of():jdbc.query("""
      SELECT a.code,a.name,av.id,COALESCE(av.display_value,av.text_value,av.integer_value::text,av.decimal_value::text,av.boolean_value::text),count(DISTINCT p.id),a.display_order,av.display_order
      FROM product p JOIN product_sku d ON d.product_id=p.id AND d.is_default AND d.status='ACTIVE'
      JOIN (SELECT product_id,attribute_id,value_id FROM product_attribute_assignment UNION ALL SELECT s.product_id,x.attribute_id,x.value_id FROM product_sku_attribute_value x JOIN product_sku s ON s.id=x.product_sku_id AND s.status='ACTIVE') assigned ON assigned.product_id=p.id
      JOIN product_attribute a ON a.id=assigned.attribute_id AND a.status='ACTIVE' AND a.is_filterable
      JOIN product_attribute_value av ON av.id=assigned.value_id
      WHERE p.id IN (SELECT p.id """+" "+where+") GROUP BY a.code,a.name,av.id,a.display_order,av.display_order,av.display_value,av.text_value,av.integer_value,av.decimal_value,av.boolean_value ORDER BY a.display_order,a.code,av.display_order,4,av.id LIMIT "+(remaining+1),(r,n)->new Object[]{r.getString(1),r.getString(2),r.getObject(3,UUID.class),r.getString(4),r.getLong(5)},args.toArray());
    boolean truncated=cats.size()>200||rows.size()>remaining;var grouped=new LinkedHashMap<String,AttributeFacet>();for(Object[] row:rows.stream().limit(remaining).toList()){String code=(String)row[0];var facet=grouped.computeIfAbsent(code,k->new AttributeFacet(code,(String)row[1],new ArrayList<>()));@SuppressWarnings("unchecked") var values=(List<FacetBucket>)facet.values();UUID value=(UUID)row[2];boolean selected=q.attributes().getOrDefault(code,List.of()).contains(value);values.add(new FacetBucket(value.toString(),(String)row[3],selected,(Long)row[4]));}
    var attributes=grouped.values().stream().map(f->new AttributeFacet(f.code(),f.name(),List.copyOf(f.values()))).toList();return new Facets(cats.stream().limit(200).toList(),attributes,avail,price,truncated);
  }
  private static List<Object> append(List<Object>a,Object x){var n=new ArrayList<>(a);if(x!=null)n.add(x);return n;}
  private static void placeholders(StringBuilder s,List<Object>a,Collection<?> v){int i=0;for(Object x:v){if(i++>0)s.append(',');s.append('?');a.add(x);}}
  private static Money money(BigDecimal n,String c){return new Money(fmt(n),c);}
  private static String fmt(BigDecimal n){return n==null?null:n.setScale(2).toPlainString();}
}

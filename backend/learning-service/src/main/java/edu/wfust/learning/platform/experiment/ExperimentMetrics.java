package edu.wfust.learning.platform.experiment;

import org.springframework.stereotype.Component;
import java.util.*;

/** Character-token metrics for Chinese course answers; reference-based, not an LLM judge. */
@Component
public class ExperimentMetrics {
    public record Scores(Double rougeL, Double bleu4, Double entityRecall, String tokenizer) { }
    public Scores evaluate(String answer,String reference,List<String> entities) {
        List<String> actual=tokens(answer), expected=tokens(reference);
        Double rouge=null,bleu=null,recall=null;
        if(!expected.isEmpty()) {
            int[] previous=new int[expected.size()+1];
            for(String token:actual){int[] next=new int[expected.size()+1];for(int j=1;j<=expected.size();j++)next[j]=token.equals(expected.get(j-1))?previous[j-1]+1:Math.max(previous[j],next[j-1]);previous=next;}
            int lcs=previous[expected.size()];
            rouge=actual.isEmpty()?0.0:round(2.0*lcs/(actual.size()+expected.size()));
            bleu=round(bleu(actual,expected));
        }
        if(entities!=null){var clean=entities.stream().filter(Objects::nonNull).map(String::strip).filter(s->!s.isEmpty()).distinct().toList();if(!clean.isEmpty()){String a=answer==null?"":answer;recall=round(clean.stream().filter(a::contains).count()/(double)clean.size());}}
        return new Scores(rouge,bleu,recall,"unicode-character-v1");
    }
    private List<String> tokens(String text) {
        if(text==null)return List.of();
        return text.toLowerCase(Locale.ROOT).codePoints().filter(Character::isLetterOrDigit).limit(4000).mapToObj(cp->new String(Character.toChars(cp))).toList();
    }
    private double bleu(List<String>a,List<String>r){
        if(a.isEmpty())return 0;
        int orders=Math.min(4,Math.min(a.size(),r.size()));double sum=0;
        for(int n=1;n<=orders;n++){Map<String,Integer>actual=grams(a,n),reference=grams(r,n);int matches=actual.entrySet().stream().mapToInt(e->Math.min(e.getValue(),reference.getOrDefault(e.getKey(),0))).sum();int count=a.size()-n+1;if(matches==0)return 0;sum+=Math.log(matches/(double)count);}
        return Math.exp(Math.min(0,1-r.size()/(double)a.size())+sum/orders);
    }
    private Map<String,Integer> grams(List<String>list,int n){var counts=new HashMap<String,Integer>();for(int i=0;i<=list.size()-n;i++)counts.merge(String.join("\u0001",list.subList(i,i+n)),1,Integer::sum);return counts;}
    private static double round(double value){return Math.round(value*1_000_000.0)/1_000_000.0;}
    public static long percentile95(List<Long>samples){if(samples.isEmpty())return 0;var sorted=samples.stream().sorted().toList();return sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*.95)-1));}
}

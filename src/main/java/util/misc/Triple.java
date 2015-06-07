package util.misc;

/**
 * borrowed from berkeley nlp libraries which we were told was apache licensed
 */
public class Triple<S,T,U> {
	public S first;
	public T second;
	public U third;
	
	public Triple(S first, T second, U third) {
		this.first = first;
		this.second = second;
		this.third = third;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((first == null) ? 0 : first.hashCode());
		result = prime * result + ((second == null) ? 0 : second.hashCode());
		result = prime * result + ((third == null) ? 0 : third.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Triple other = (Triple) obj;
		if (first == null) {
			if (other.first != null)
				return false;
		} else if (!first.equals(other.first))
			return false;
		if (second == null) {
			if (other.second != null)
				return false;
		} else if (!second.equals(other.second))
			return false;
		if (third == null) {
			if (other.third != null)
				return false;
		} else if (!third.equals(other.third))
			return false;
		return true;
	}
	
	public String toString() {
		return String.format("(%s,%s,%s)",first,second,third);
	}

	public static <S,T,U> Triple<S,T,U> makeTriple(S s, T t, U u) {
		// TODO Auto-generated method stub
		return new Triple<S, T, U>(s,t,u);
	}
	
}
